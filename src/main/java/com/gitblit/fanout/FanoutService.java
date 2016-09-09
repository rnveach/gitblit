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
package com.gitblit.fanout;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for Fanout service implementations.
 *
 * Subclass implementations can be used as a Sparkleshare PubSub notification
 * server. This allows Sparkleshare to be used in conjunction with Gitblit
 * behind a corporate firewall that restricts or prohibits client internet
 * access to the default Sparkleshare PubSub server:
 * notifications.sparkleshare.org
 *
 * @author James Moger
 * 
 */
public abstract class FanoutService implements Runnable {

	private final static Logger logger = LoggerFactory.getLogger(FanoutService.class);

	public final static int DEFAULT_PORT = 17000;

	protected final static int serviceTimeout = 5000;

	protected final String host;
	protected final int port;
	protected final String name;

	private Thread serviceThread;

	private final Map<String, FanoutServiceConnection> connections;
	private final Map<String, Set<FanoutServiceConnection>> subscriptions;

	protected final AtomicBoolean isRunning;
	private final AtomicBoolean strictRequestTermination;
	private final AtomicBoolean allowAllChannelAnnouncements;
	private final AtomicInteger concurrentConnectionLimit;

	private final Date bootDate;
	private final AtomicLong rejectedConnectionCount;
	private final AtomicInteger peakConnectionCount;
	private final AtomicLong totalConnections;
	private final AtomicLong totalAnnouncements;
	private final AtomicLong totalMessages;
	private final AtomicLong totalSubscribes;
	private final AtomicLong totalUnsubscribes;
	private final AtomicLong totalPings;

	protected FanoutService(String host, int port, String name) {
		this.host = host;
		this.port = port;
		this.name = name;

		this.connections = new ConcurrentHashMap<String, FanoutServiceConnection>();
		this.subscriptions = new ConcurrentHashMap<String, Set<FanoutServiceConnection>>();
		this.subscriptions.put(FanoutConstants.CH_ALL,
				new ConcurrentSkipListSet<FanoutServiceConnection>());

		this.isRunning = new AtomicBoolean(false);
		this.strictRequestTermination = new AtomicBoolean(false);
		this.allowAllChannelAnnouncements = new AtomicBoolean(false);
		this.concurrentConnectionLimit = new AtomicInteger(0);

		this.bootDate = new Date();
		this.rejectedConnectionCount = new AtomicLong(0);
		this.peakConnectionCount = new AtomicInteger(0);
		this.totalConnections = new AtomicLong(0);
		this.totalAnnouncements = new AtomicLong(0);
		this.totalMessages = new AtomicLong(0);
		this.totalSubscribes = new AtomicLong(0);
		this.totalUnsubscribes = new AtomicLong(0);
		this.totalPings = new AtomicLong(0);
	}

	/*
	 * Abstract methods
	 */

	protected abstract boolean isConnected();

	protected abstract boolean connect();

	protected abstract void listen() throws IOException;

	protected abstract void disconnect();

	/**
	 * Returns true if the service requires \n request termination.
	 *
	 * @return true if request requires \n termination
	 */
	public boolean isStrictRequestTermination() {
		return this.strictRequestTermination.get();
	}

	/**
	 * Control the termination of fanout requests. If true, fanout requests must
	 * be terminated with \n. If false, fanout requests may be terminated with
	 * \n, \r, \r\n, or \n\r. This is useful for debugging with a telnet client.
	 *
	 * @param isStrictTermination
	 */
	public void setStrictRequestTermination(boolean isStrictTermination) {
		this.strictRequestTermination.set(isStrictTermination);
	}

	/**
	 * Returns the maximum allowable concurrent fanout connections.
	 *
	 * @return the maximum allowable concurrent connection count
	 */
	public int getConcurrentConnectionLimit() {
		return this.concurrentConnectionLimit.get();
	}

	/**
	 * Sets the maximum allowable concurrent fanout connection count.
	 *
	 * @param value
	 */
	public void setConcurrentConnectionLimit(int value) {
		this.concurrentConnectionLimit.set(value);
	}

	/**
	 * Returns true if connections are allowed to announce on the all channel.
	 *
	 * @return true if connections are allowed to announce on the all channel
	 */
	public boolean allowAllChannelAnnouncements() {
		return this.allowAllChannelAnnouncements.get();
	}

	/**
	 * Allows/prohibits connections from announcing on the ALL channel.
	 *
	 * @param value
	 */
	public void setAllowAllChannelAnnouncements(boolean value) {
		this.allowAllChannelAnnouncements.set(value);
	}

	/**
	 * Returns the current connections
	 *
	 * @param channel
	 * @return map of current connections keyed by their id
	 */
	public Map<String, FanoutServiceConnection> getCurrentConnections() {
		return this.connections;
	}

	/**
	 * Returns all subscriptions
	 *
	 * @return map of current subscriptions keyed by channel name
	 */
	public Map<String, Set<FanoutServiceConnection>> getCurrentSubscriptions() {
		return this.subscriptions;
	}

	/**
	 * Returns the subscriptions for the specified channel
	 *
	 * @param channel
	 * @return set of subscribed connections for the specified channel
	 */
	public Set<FanoutServiceConnection> getCurrentSubscriptions(String channel) {
		return this.subscriptions.get(channel);
	}

	/**
	 * Returns the runtime statistics object for this service.
	 *
	 * @return stats
	 */
	public FanoutStats getStatistics() {
		final FanoutStats stats = new FanoutStats();

		// settings
		stats.allowAllChannelAnnouncements = allowAllChannelAnnouncements();
		stats.concurrentConnectionLimit = getConcurrentConnectionLimit();
		stats.strictRequestTermination = isStrictRequestTermination();

		// runtime stats
		stats.bootDate = this.bootDate;
		stats.rejectedConnectionCount = this.rejectedConnectionCount.get();
		stats.peakConnectionCount = this.peakConnectionCount.get();
		stats.totalConnections = this.totalConnections.get();
		stats.totalAnnouncements = this.totalAnnouncements.get();
		stats.totalMessages = this.totalMessages.get();
		stats.totalSubscribes = this.totalSubscribes.get();
		stats.totalUnsubscribes = this.totalUnsubscribes.get();
		stats.totalPings = this.totalPings.get();
		stats.currentConnections = this.connections.size();
		stats.currentChannels = this.subscriptions.size();
		stats.currentSubscriptions = this.subscriptions.size() * this.connections.size();
		return stats;
	}

	/**
	 * Returns true if the service is ready.
	 *
	 * @return true, if the service is ready
	 */
	public boolean isReady() {
		if (this.isRunning.get()) {
			return isConnected();
		}
		return false;
	}

	/**
	 * Start the Fanout service thread and immediatel return.
	 *
	 */
	public void start() {
		if (this.isRunning.get()) {
			logger.warn(MessageFormat.format("{0} is already running", this.name));
			return;
		}
		this.serviceThread = new Thread(this);
		this.serviceThread.setName(MessageFormat.format("{0} {1}:{2,number,0}", this.name,
				this.host == null ? "all" : this.host, this.port));
		this.serviceThread.start();
	}

	/**
	 * Start the Fanout service thread and wait until it is accepting
	 * connections.
	 *
	 */
	public void startSynchronously() {
		start();
		while (!isReady()) {
			try {
				Thread.sleep(100);
			}
			catch (final Exception e) {
			}
		}
	}

	/**
	 * Stop the Fanout service. This method returns when the service has been
	 * completely shutdown.
	 */
	public void stop() {
		if (!this.isRunning.get()) {
			logger.warn(MessageFormat.format("{0} is not running", this.name));
			return;
		}
		logger.info(MessageFormat.format("stopping {0}...", this.name));
		this.isRunning.set(false);
		try {
			if (this.serviceThread != null) {
				this.serviceThread.join();
				this.serviceThread = null;
			}
		}
		catch (final InterruptedException e1) {
			logger.error("", e1);
		}
		logger.info(MessageFormat.format("stopped {0}", this.name));
	}

	/**
	 * Main execution method of the service
	 */
	@Override
	public final void run() {
		disconnect();
		resetState();
		this.isRunning.set(true);
		while (this.isRunning.get()) {
			if (connect()) {
				try {
					listen();
				}
				catch (final IOException e) {
					logger.error(MessageFormat.format("error processing {0}", this.name), e);
					this.isRunning.set(false);
				}
			} else {
				try {
					Thread.sleep(serviceTimeout);
				}
				catch (final InterruptedException x) {
				}
			}
		}
		disconnect();
		resetState();
	}

	protected void resetState() {
		// reset state data
		this.connections.clear();
		this.subscriptions.clear();
		this.rejectedConnectionCount.set(0);
		this.peakConnectionCount.set(0);
		this.totalConnections.set(0);
		this.totalAnnouncements.set(0);
		this.totalMessages.set(0);
		this.totalSubscribes.set(0);
		this.totalUnsubscribes.set(0);
		this.totalPings.set(0);
	}

	/**
	 * Configure the client connection socket.
	 *
	 * @param socket
	 * @throws SocketException
	 */
	protected void configureClientSocket(Socket socket) throws SocketException {
		socket.setKeepAlive(true);
		socket.setSoLinger(true, 0); // immediately discard any remaining data
	}

	/**
	 * Add the connection to the connections map.
	 *
	 * @param connection
	 * @return false if the connection was rejected due to too many concurrent
	 *         connections
	 */
	protected boolean addConnection(FanoutServiceConnection connection) {
		final int limit = getConcurrentConnectionLimit();
		if ((limit > 0) && (this.connections.size() > limit)) {
			logger.info(MessageFormat.format(
					"hit {0,number,0} connection limit, rejecting fanout connection",
					this.concurrentConnectionLimit));
			increment(this.rejectedConnectionCount);
			connection.busy();
			return false;
		}

		// add the connection to our map
		this.connections.put(connection.id, connection);

		// track peak number of concurrent connections
		if (this.connections.size() > this.peakConnectionCount.get()) {
			this.peakConnectionCount.set(this.connections.size());
		}

		logger.info("fanout new connection " + connection.id);
		connection.connected();
		return true;
	}

	/**
	 * Remove the connection from the connections list and from subscriptions.
	 *
	 * @param connection
	 */
	protected void removeConnection(FanoutServiceConnection connection) {
		this.connections.remove(connection.id);
		final Iterator<Map.Entry<String, Set<FanoutServiceConnection>>> itr = this.subscriptions
				.entrySet().iterator();
		while (itr.hasNext()) {
			final Map.Entry<String, Set<FanoutServiceConnection>> entry = itr.next();
			final Set<FanoutServiceConnection> subscriptions = entry.getValue();
			subscriptions.remove(connection);
			if (!FanoutConstants.CH_ALL.equals(entry.getKey())) {
				if (subscriptions.size() == 0) {
					itr.remove();
					logger.info(MessageFormat.format("fanout remove channel {0}, no subscribers",
							entry.getKey()));
				}
			}
		}
		logger.info(MessageFormat.format("fanout connection {0} removed", connection.id));
	}

	/**
	 * Tests to see if the connection is being monitored by the service.
	 *
	 * @param connection
	 * @return true if the service is monitoring the connection
	 */
	protected boolean hasConnection(FanoutServiceConnection connection) {
		return this.connections.containsKey(connection.id);
	}

	/**
	 * Reply to a connection on the specified channel.
	 *
	 * @param connection
	 * @param channel
	 * @param message
	 * @return the reply
	 */
	protected String reply(FanoutServiceConnection connection, String channel, String message) {
		if ((channel != null) && (channel.length() > 0)) {
			increment(this.totalMessages);
		}
		return connection.reply(channel, message);
	}

	/**
	 * Service method to broadcast a message to all connections.
	 *
	 * @param message
	 */
	public void broadcastAll(String message) {
		broadcast(this.connections.values(), FanoutConstants.CH_ALL, message);
		increment(this.totalAnnouncements);
	}

	/**
	 * Service method to broadcast a message to connections subscribed to the
	 * channel.
	 *
	 * @param message
	 */
	public void broadcast(String channel, String message) {
		final List<FanoutServiceConnection> connections = new ArrayList<FanoutServiceConnection>(
				this.subscriptions.get(channel));
		broadcast(connections, channel, message);
		increment(this.totalAnnouncements);
	}

	/**
	 * Broadcast a message to connections subscribed to the specified channel.
	 *
	 * @param connections
	 * @param channel
	 * @param message
	 */
	protected void broadcast(Collection<FanoutServiceConnection> connections, String channel,
			String message) {
		for (final FanoutServiceConnection connection : connections) {
			reply(connection, channel, message);
		}
	}

	/**
	 * Process an incoming Fanout request.
	 *
	 * @param connection
	 * @param req
	 * @return the reply to the request, may be null
	 */
	protected String processRequest(FanoutServiceConnection connection, String req) {
		logger.info(MessageFormat.format("fanout request from {0}: {1}", connection.id, req));
		final String[] fields = req.split(" ", 3);
		final String action = fields[0];
		final String channel = fields.length >= 2 ? fields[1] : null;
		final String message = fields.length >= 3 ? fields[2] : null;
		try {
			return processRequest(connection, action, channel, message);
		}
		catch (final IllegalArgumentException e) {
			// invalid action
			logger.error(MessageFormat.format("fanout connection {0} requested invalid action {1}",
					connection.id, action));
			logger.error(asHexArray(req));
		}
		return null;
	}

	/**
	 * Process the Fanout request.
	 *
	 * @param connection
	 * @param action
	 * @param channel
	 * @param message
	 * @return the reply to the request, may be null
	 * @throws IllegalArgumentException
	 */
	protected String processRequest(FanoutServiceConnection connection, String action,
			String channel, String message) throws IllegalArgumentException {
		if ("ping".equals(action)) {
			// ping
			increment(this.totalPings);
			return reply(connection, null, "" + System.currentTimeMillis());
		} else if ("info".equals(action)) {
			// info
			final String info = getStatistics().info();
			return reply(connection, null, info);
		} else if ("announce".equals(action)) {
			// announcement
			if (!this.allowAllChannelAnnouncements.get() && FanoutConstants.CH_ALL.equals(channel)) {
				// prohibiting connection-sourced all announcements
				logger.warn(MessageFormat.format(
						"fanout connection {0} attempted to announce {1} on ALL channel",
						connection.id, message));
			} else if ("debug".equals(channel)) {
				// prohibiting connection-sourced debug announcements
				logger.warn(MessageFormat.format(
						"fanout connection {0} attempted to announce {1} on DEBUG channel",
						connection.id, message));
			} else {
				// acceptable announcement
				final List<FanoutServiceConnection> connections = new ArrayList<FanoutServiceConnection>(
						this.subscriptions.get(channel));
				connections.remove(connection); // remove announcer
				broadcast(connections, channel, message);
				increment(this.totalAnnouncements);
			}
		} else if ("subscribe".equals(action)) {
			// subscribe
			if (!this.subscriptions.containsKey(channel)) {
				logger.info(MessageFormat.format("fanout new channel {0}", channel));
				this.subscriptions.put(channel,
						new ConcurrentSkipListSet<FanoutServiceConnection>());
			}
			this.subscriptions.get(channel).add(connection);
			logger.debug(MessageFormat.format("fanout connection {0} subscribed to channel {1}",
					connection.id, channel));
			increment(this.totalSubscribes);
		} else if ("unsubscribe".equals(action)) {
			// unsubscribe
			if (this.subscriptions.containsKey(channel)) {
				this.subscriptions.get(channel).remove(connection);
				if (this.subscriptions.get(channel).size() == 0) {
					this.subscriptions.remove(channel);
				}
				increment(this.totalUnsubscribes);
			}
		} else {
			// invalid action
			throw new IllegalArgumentException(action);
		}
		return null;
	}

	private static String asHexArray(String req) {
		final StringBuilder sb = new StringBuilder();
		for (final char c : req.toCharArray()) {
			sb.append(Integer.toHexString(c)).append(' ');
		}
		return "[ " + sb.toString().trim() + " ]";
	}

	/**
	 * Increment a long and prevent negative rollover.
	 *
	 * @param counter
	 */
	private static void increment(AtomicLong counter) {
		final long v = counter.incrementAndGet();
		if (v < 0) {
			counter.set(0);
		}
	}

	@Override
	public String toString() {
		return this.name;
	}
}