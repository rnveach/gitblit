/*
 * Copyright 2013 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.tickets;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import redis.clients.jedis.Client;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisException;

import com.gitblit.Keys;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IPluginManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Attachment;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Implementation of a ticket service based on a Redis key-value store. All
 * tickets are persisted in the Redis store so it must be configured for
 * durability otherwise tickets are lost on a flush or restart. Tickets are
 * indexed with Lucene and all queries are executed against the Lucene index.
 *
 * @author James Moger
 *
 */
@Singleton
public class RedisTicketService extends ITicketService {

	private final JedisPool pool;

	private enum KeyType {
		journal,
		ticket,
		counter
	}

	@Inject
	public RedisTicketService(IRuntimeManager runtimeManager, IPluginManager pluginManager,
			INotificationManager notificationManager, IUserManager userManager,
			IRepositoryManager repositoryManager) {

		super(runtimeManager, pluginManager, notificationManager, userManager, repositoryManager);

		final String redisUrl = this.settings.getString(Keys.tickets.redis.url, "");
		this.pool = createPool(redisUrl);
	}

	@Override
	public RedisTicketService start() {
		this.log.info("{} started", getClass().getSimpleName());
		if (!isReady()) {
			this.log.warn("{} is not ready!", getClass().getSimpleName());
		}
		return this;
	}

	@Override
	protected void resetCachesImpl() {
	}

	@Override
	protected void resetCachesImpl(RepositoryModel repository) {
	}

	@Override
	protected void close() {
		this.pool.destroy();
	}

	@Override
	public boolean isReady() {
		return this.pool != null;
	}

	/**
	 * Constructs a key for use with a key-value data store.
	 *
	 * @param key
	 * @param repository
	 * @param id
	 * @return a key
	 */
	private static String key(RepositoryModel repository, KeyType key, String id) {
		final StringBuilder sb = new StringBuilder();
		sb.append(repository.name).append(':');
		sb.append(key.name());
		if (!StringUtils.isEmpty(id)) {
			sb.append(':');
			sb.append(id);
		}
		return sb.toString();
	}

	/**
	 * Constructs a key for use with a key-value data store.
	 *
	 * @param key
	 * @param repository
	 * @param id
	 * @return a key
	 */
	private static String key(RepositoryModel repository, KeyType key, long id) {
		return key(repository, key, "" + id);
	}

	private static boolean isNull(String value) {
		return (value == null) || "nil".equals(value);
	}

	private String getUrl() {
		Jedis jedis = this.pool.getResource();
		try {
			if (jedis != null) {
				final Client client = jedis.getClient();
				return client.getHost() + ":" + client.getPort() + "/" + client.getDB();
			}
		}
		catch (final JedisException e) {
			this.pool.returnBrokenResource(jedis);
			jedis = null;
		}
		finally {
			if (jedis != null) {
				this.pool.returnResource(jedis);
			}
		}
		return null;
	}

	/**
	 * Ensures that we have a ticket for this ticket id.
	 *
	 * @param repository
	 * @param ticketId
	 * @return true if the ticket exists
	 */
	@Override
	public boolean hasTicket(RepositoryModel repository, long ticketId) {
		if (ticketId <= 0L) {
			return false;
		}
		Jedis jedis = this.pool.getResource();
		if (jedis == null) {
			return false;
		}
		try {
			final Boolean exists = jedis.exists(key(repository, KeyType.journal, ticketId));
			return (exists != null) && exists;
		}
		catch (final JedisException e) {
			this.log.error("failed to check hasTicket from Redis @ " + getUrl(), e);
			this.pool.returnBrokenResource(jedis);
			jedis = null;
		}
		finally {
			if (jedis != null) {
				this.pool.returnResource(jedis);
			}
		}
		return false;
	}

	@Override
	public Set<Long> getIds(RepositoryModel repository) {
		final Set<Long> ids = new TreeSet<Long>();
		Jedis jedis = this.pool.getResource();
		try {// account for migrated tickets
			final Set<String> keys = jedis.keys(key(repository, KeyType.journal, "*"));
			for (final String tkey : keys) {
				// {repo}:journal:{id}
				final String id = tkey.split(":")[2];
				final long ticketId = Long.parseLong(id);
				ids.add(ticketId);
			}
		}
		catch (final JedisException e) {
			this.log.error("failed to assign new ticket id in Redis @ " + getUrl(), e);
			this.pool.returnBrokenResource(jedis);
			jedis = null;
		}
		finally {
			if (jedis != null) {
				this.pool.returnResource(jedis);
			}
		}
		return ids;
	}

	/**
	 * Assigns a new ticket id.
	 *
	 * @param repository
	 * @return a new long ticket id
	 */
	@Override
	public synchronized long assignNewId(RepositoryModel repository) {
		Jedis jedis = this.pool.getResource();
		try {
			final String key = key(repository, KeyType.counter, null);
			final String val = jedis.get(key);
			if (isNull(val)) {
				long lastId = 0;
				final Set<Long> ids = getIds(repository);
				for (final long id : ids) {
					if (id > lastId) {
						lastId = id;
					}
				}
				jedis.set(key, "" + lastId);
			}
			final long ticketNumber = jedis.incr(key);
			return ticketNumber;
		}
		catch (final JedisException e) {
			this.log.error("failed to assign new ticket id in Redis @ " + getUrl(), e);
			this.pool.returnBrokenResource(jedis);
			jedis = null;
		}
		finally {
			if (jedis != null) {
				this.pool.returnResource(jedis);
			}
		}
		return 0L;
	}

	/**
	 * Returns all the tickets in the repository. Querying tickets from the
	 * repository requires deserializing all tickets. This is an expensive
	 * process and not recommended. Tickets should be indexed by Lucene and
	 * queries should be executed against that index.
	 *
	 * @param repository
	 * @param filter
	 *            optional filter to only return matching results
	 * @return a list of tickets
	 */
	@Override
	public List<TicketModel> getTickets(RepositoryModel repository, TicketFilter filter) {
		Jedis jedis = this.pool.getResource();
		final List<TicketModel> list = new ArrayList<TicketModel>();
		if (jedis == null) {
			return list;
		}
		try {
			// Deserialize each journal, build the ticket, and optionally filter
			final Set<String> keys = jedis.keys(key(repository, KeyType.journal, "*"));
			for (final String key : keys) {
				// {repo}:journal:{id}
				final String id = key.split(":")[2];
				final long ticketId = Long.parseLong(id);
				final List<Change> changes = getJournal(jedis, repository, ticketId);
				if (ArrayUtils.isEmpty(changes)) {
					this.log.warn("Empty journal for {}:{}", repository, ticketId);
					continue;
				}
				final TicketModel ticket = TicketModel.buildTicket(changes);
				ticket.project = repository.projectPath;
				ticket.repository = repository.name;
				ticket.number = ticketId;

				// add the ticket, conditionally, to the list
				if (filter == null) {
					list.add(ticket);
				} else {
					if (filter.accept(ticket)) {
						list.add(ticket);
					}
				}
			}

			// sort the tickets by creation
			Collections.sort(list);
		}
		catch (final JedisException e) {
			this.log.error("failed to retrieve tickets from Redis @ " + getUrl(), e);
			this.pool.returnBrokenResource(jedis);
			jedis = null;
		}
		finally {
			if (jedis != null) {
				this.pool.returnResource(jedis);
			}
		}
		return list;
	}

	/**
	 * Retrieves the ticket from the repository.
	 *
	 * @param repository
	 * @param ticketId
	 * @return a ticket, if it exists, otherwise null
	 */
	@Override
	protected TicketModel getTicketImpl(RepositoryModel repository, long ticketId) {
		Jedis jedis = this.pool.getResource();
		if (jedis == null) {
			return null;
		}

		try {
			final List<Change> changes = getJournal(jedis, repository, ticketId);
			if (ArrayUtils.isEmpty(changes)) {
				this.log.warn("Empty journal for {}:{}", repository, ticketId);
				return null;
			}
			final TicketModel ticket = TicketModel.buildTicket(changes);
			ticket.project = repository.projectPath;
			ticket.repository = repository.name;
			ticket.number = ticketId;
			this.log.debug("rebuilt ticket {} from Redis @ {}", ticketId, getUrl());
			return ticket;
		}
		catch (final JedisException e) {
			this.log.error("failed to retrieve ticket from Redis @ " + getUrl(), e);
			this.pool.returnBrokenResource(jedis);
			jedis = null;
		}
		finally {
			if (jedis != null) {
				this.pool.returnResource(jedis);
			}
		}
		return null;
	}

	/**
	 * Retrieves the journal for the ticket.
	 *
	 * @param repository
	 * @param ticketId
	 * @return a journal, if it exists, otherwise null
	 */
	@Override
	protected List<Change> getJournalImpl(RepositoryModel repository, long ticketId) {
		Jedis jedis = this.pool.getResource();
		if (jedis == null) {
			return null;
		}

		try {
			final List<Change> changes = getJournal(jedis, repository, ticketId);
			if (ArrayUtils.isEmpty(changes)) {
				this.log.warn("Empty journal for {}:{}", repository, ticketId);
				return null;
			}
			return changes;
		}
		catch (final JedisException e) {
			this.log.error("failed to retrieve journal from Redis @ " + getUrl(), e);
			this.pool.returnBrokenResource(jedis);
			jedis = null;
		}
		finally {
			if (jedis != null) {
				this.pool.returnResource(jedis);
			}
		}
		return null;
	}

	/**
	 * Returns the journal for the specified ticket.
	 *
	 * @param repository
	 * @param ticketId
	 * @return a list of changes
	 */
	private static List<Change> getJournal(Jedis jedis, RepositoryModel repository, long ticketId)
			throws JedisException {
		if (ticketId <= 0L) {
			return new ArrayList<Change>();
		}
		final List<String> entries = jedis
				.lrange(key(repository, KeyType.journal, ticketId), 0, -1);
		if (entries.size() > 0) {
			// build a json array from the individual entries
			final StringBuilder sb = new StringBuilder();
			sb.append("[");
			for (final String entry : entries) {
				sb.append(entry).append(',');
			}
			sb.setLength(sb.length() - 1);
			sb.append(']');
			final String journal = sb.toString();

			return TicketSerializer.deserializeJournal(journal);
		}
		return new ArrayList<Change>();
	}

	@Override
	public boolean supportsAttachments() {
		return false;
	}

	/**
	 * Retrieves the specified attachment from a ticket.
	 *
	 * @param repository
	 * @param ticketId
	 * @param filename
	 * @return an attachment, if found, null otherwise
	 */
	@Override
	public Attachment getAttachment(RepositoryModel repository, long ticketId, String filename) {
		return null;
	}

	/**
	 * Deletes a ticket.
	 *
	 * @param ticket
	 * @return true if successful
	 */
	@Override
	protected boolean deleteTicketImpl(RepositoryModel repository, TicketModel ticket,
			String deletedBy) {
		boolean success = false;
		if (ticket == null) {
			throw new RuntimeException("must specify a ticket!");
		}

		Jedis jedis = this.pool.getResource();
		if (jedis == null) {
			return false;
		}

		try {
			// atomically remove ticket
			final Transaction t = jedis.multi();
			t.del(key(repository, KeyType.ticket, ticket.number));
			t.del(key(repository, KeyType.journal, ticket.number));
			t.exec();

			success = true;
			this.log.debug("deleted ticket {} from Redis @ {}", "" + ticket.number, getUrl());
		}
		catch (final JedisException e) {
			this.log.error("failed to delete ticket from Redis @ " + getUrl(), e);
			this.pool.returnBrokenResource(jedis);
			jedis = null;
		}
		finally {
			if (jedis != null) {
				this.pool.returnResource(jedis);
			}
		}

		return success;
	}

	/**
	 * Commit a ticket change to the repository.
	 *
	 * @param repository
	 * @param ticketId
	 * @param change
	 * @return true, if the change was committed
	 */
	@Override
	protected boolean commitChangeImpl(RepositoryModel repository, long ticketId, Change change) {
		Jedis jedis = this.pool.getResource();
		if (jedis == null) {
			return false;
		}
		try {
			final List<Change> changes = getJournal(jedis, repository, ticketId);
			changes.add(change);
			// build a new effective ticket from the changes
			final TicketModel ticket = TicketModel.buildTicket(changes);

			final String object = TicketSerializer.serialize(ticket);
			final String journal = TicketSerializer.serialize(change);

			// atomically store ticket
			final Transaction t = jedis.multi();
			t.set(key(repository, KeyType.ticket, ticketId), object);
			t.rpush(key(repository, KeyType.journal, ticketId), journal);
			t.exec();

			this.log.debug("updated ticket {} in Redis @ {}", "" + ticketId, getUrl());
			return true;
		}
		catch (final JedisException e) {
			this.log.error("failed to update ticket cache in Redis @ " + getUrl(), e);
			this.pool.returnBrokenResource(jedis);
			jedis = null;
		}
		finally {
			if (jedis != null) {
				this.pool.returnResource(jedis);
			}
		}
		return false;
	}

	/**
	 * Deletes all Tickets for the rpeository from the Redis key-value store.
	 *
	 */
	@Override
	protected boolean deleteAllImpl(RepositoryModel repository) {
		Jedis jedis = this.pool.getResource();
		if (jedis == null) {
			return false;
		}

		boolean success = false;
		try {
			final Set<String> keys = jedis.keys(repository.name + ":*");
			if (keys.size() > 0) {
				final Transaction t = jedis.multi();
				t.del(keys.toArray(new String[keys.size()]));
				t.exec();
			}
			success = true;
		}
		catch (final JedisException e) {
			this.log.error("failed to delete all tickets in Redis @ " + getUrl(), e);
			this.pool.returnBrokenResource(jedis);
			jedis = null;
		}
		finally {
			if (jedis != null) {
				this.pool.returnResource(jedis);
			}
		}
		return success;
	}

	@Override
	protected boolean renameImpl(RepositoryModel oldRepository, RepositoryModel newRepository) {
		Jedis jedis = this.pool.getResource();
		if (jedis == null) {
			return false;
		}

		boolean success = false;
		try {
			final Set<String> oldKeys = jedis.keys(oldRepository.name + ":*");
			final Transaction t = jedis.multi();
			for (final String oldKey : oldKeys) {
				final String newKey = newRepository.name + oldKey.substring(oldKey.indexOf(':'));
				t.rename(oldKey, newKey);
			}
			t.exec();
			success = true;
		}
		catch (final JedisException e) {
			this.log.error("failed to rename tickets in Redis @ " + getUrl(), e);
			this.pool.returnBrokenResource(jedis);
			jedis = null;
		}
		finally {
			if (jedis != null) {
				this.pool.returnResource(jedis);
			}
		}
		return success;
	}

	private JedisPool createPool(String url) {
		JedisPool pool = null;
		if (!StringUtils.isEmpty(url)) {
			try {
				final URI uri = URI.create(url);
				if ((uri.getScheme() != null) && uri.getScheme().equalsIgnoreCase("redis")) {
					int database = Protocol.DEFAULT_DATABASE;
					String password = null;
					if (uri.getUserInfo() != null) {
						password = uri.getUserInfo().split(":", 2)[1];
					}
					if (uri.getPath().indexOf('/') > -1) {
						database = Integer.parseInt(uri.getPath().split("/", 2)[1]);
					}
					pool = new JedisPool(new GenericObjectPoolConfig(), uri.getHost(),
							uri.getPort(), Protocol.DEFAULT_TIMEOUT, password, database);
				} else {
					pool = new JedisPool(url);
				}
			}
			catch (final JedisException e) {
				this.log.error("failed to create a Redis pool!", e);
			}
		}
		return pool;
	}

	@Override
	public String toString() {
		final String url = getUrl();
		return getClass().getSimpleName() + " (" + (url == null ? "DISABLED" : url) + ")";
	}
}
