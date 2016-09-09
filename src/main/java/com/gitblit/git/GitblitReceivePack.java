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
package com.gitblit.git;

import static org.eclipse.jgit.transport.BasePackPushConnection.CAPABILITY_SIDE_BAND_64K;
import groovy.lang.Binding;
import groovy.util.GroovyScriptEngine;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.eclipse.jgit.transport.ReceivePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.client.Translation;
import com.gitblit.extensions.ReceiveHook;
import com.gitblit.manager.IGitblit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.Field;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.TicketModel.TicketLink;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.BranchTicketService;
import com.gitblit.tickets.ITicketService;
import com.gitblit.tickets.TicketNotifier;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.ClientLogger;
import com.gitblit.utils.CommitCache;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.RefLogUtils;
import com.gitblit.utils.StringUtils;

/**
 * GitblitReceivePack processes receive commands. It also executes Groovy pre-
 * and post- receive hooks.
 *
 * The general execution flow is:
 * <ol>
 * <li>onPreReceive()</li>
 * <li>executeCommands()</li>
 * <li>onPostReceive()</li>
 * </ol>
 * 
 * @author Android Open Source Project
 * @author James Moger
 *
 */
public class GitblitReceivePack extends ReceivePack implements PreReceiveHook, PostReceiveHook {

	private static final Logger LOGGER = LoggerFactory.getLogger(GitblitReceivePack.class);

	protected final RepositoryModel repository;

	protected final UserModel user;

	protected final File groovyDir;

	protected String gitblitUrl;

	protected GroovyScriptEngine gse;

	protected final IStoredSettings settings;

	protected final IGitblit gitblit;

	protected final ITicketService ticketService;

	protected final TicketNotifier ticketNotifier;

	public GitblitReceivePack(IGitblit gitblit, Repository db, RepositoryModel repository,
			UserModel user) {

		super(db);
		this.settings = gitblit.getSettings();
		this.gitblit = gitblit;
		this.repository = repository;
		this.user = user;
		this.groovyDir = gitblit.getHooksFolder();
		try {
			// set Grape root
			final File grapeRoot = gitblit.getGrapesFolder();
			grapeRoot.mkdirs();
			System.setProperty("grape.root", grapeRoot.getAbsolutePath());
			this.gse = new GroovyScriptEngine(this.groovyDir.getAbsolutePath());
		}
		catch (final IOException e) {
		}

		if (gitblit.getTicketService().isAcceptingTicketUpdates(repository)) {
			this.ticketService = gitblit.getTicketService();
			this.ticketNotifier = this.ticketService.createNotifier();
		} else {
			this.ticketService = null;
			this.ticketNotifier = null;
		}

		// set advanced ref permissions
		setAllowCreates(user.canCreateRef(repository));
		setAllowDeletes(user.canDeleteRef(repository));
		setAllowNonFastForwards(user.canRewindRef(repository));

		final int maxObjectSz = this.settings.getInteger(Keys.git.maxObjectSizeLimit, -1);
		if (maxObjectSz >= 0) {
			setMaxObjectSizeLimit(maxObjectSz);
		}
		final int maxPackSz = this.settings.getInteger(Keys.git.maxPackSizeLimit, -1);
		if (maxPackSz >= 0) {
			setMaxPackSizeLimit(maxPackSz);
		}
		setCheckReceivedObjects(this.settings.getBoolean(Keys.git.checkReceivedObjects, true));
		setCheckReferencedObjectsAreReachable(this.settings.getBoolean(
				Keys.git.checkReferencedObjectsAreReachable, true));

		// setup pre and post receive hook
		setPreReceiveHook(this);
		setPostReceiveHook(this);
	}

	/**
	 * Returns true if the user is permitted to apply the receive commands to
	 * the repository.
	 *
	 * @param commands
	 * @return true if the user may push these commands
	 */
	protected boolean canPush(Collection<ReceiveCommand> commands) {
		// TODO Consider supporting branch permissions here (issue-36)
		// Not sure if that should be Gerrit-style, refs/meta/config, or
		// gitolite-style, permissions in users.conf
		//
		// How could commands be empty?
		//
		// Because a subclass, like PatchsetReceivePack, filters receive
		// commands before this method is called. This makes it possible for
		// this method to test an empty list. In this case, we assume that the
		// subclass receive pack properly enforces push restrictions. for the
		// ref.
		//
		// The empty test is not explicitly required, it's written here to
		// clarify special-case behavior.

		return commands.isEmpty() ? true : this.user.canPush(this.repository);
	}

	/**
	 * Instrumentation point where the incoming push event has been parsed,
	 * validated, objects created BUT refs have not been updated. You might use
	 * this to enforce a branch-write permissions model.
	 */
	@Override
	public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {

		if (commands.size() == 0) {
			// no receive commands to process
			// this can happen if receive pack subclasses intercept and filter
			// the commands
			LOGGER.debug("skipping pre-receive processing, no refs created, updated, or removed");
			return;
		}

		if (this.repository.isMirror) {
			// repository is a mirror
			for (final ReceiveCommand cmd : commands) {
				sendRejection(cmd,
						"Gitblit does not allow pushes to \"{0}\" because it is a mirror!",
						this.repository.name);
			}
			return;
		}

		if (this.repository.isFrozen) {
			// repository is frozen/readonly
			for (final ReceiveCommand cmd : commands) {
				sendRejection(cmd,
						"Gitblit does not allow pushes to \"{0}\" because it is frozen!",
						this.repository.name);
			}
			return;
		}

		if (!this.repository.isBare) {
			// repository has a working copy
			for (final ReceiveCommand cmd : commands) {
				sendRejection(cmd,
						"Gitblit does not allow pushes to \"{0}\" because it has a working copy!",
						this.repository.name);
			}
			return;
		}

		if (!canPush(commands)) {
			// user does not have push permissions
			for (final ReceiveCommand cmd : commands) {
				sendRejection(cmd, "User \"{0}\" does not have push permissions for \"{1}\"!",
						this.user.username, this.repository.name);
			}
			return;
		}

		if (this.repository.accessRestriction.atLeast(AccessRestrictionType.PUSH)
				&& this.repository.verifyCommitter) {
			// enforce committer verification
			if (StringUtils.isEmpty(this.user.emailAddress)) {
				// reject the push because the pushing account does not have an
				// email address
				for (final ReceiveCommand cmd : commands) {
					sendRejection(
							cmd,
							"Sorry, the account \"{0}\" does not have an email address set for committer verification!",
							this.user.username);
				}
				return;
			}

			// Optionally enforce that the committer of first parent chain
			// match the account being used to push the commits.
			//
			// This requires all merge commits are executed with the "--no-ff"
			// option to force a merge commit even if fast-forward is possible.
			// This ensures that the chain first parents has the commit
			// identity of the merging user.
			boolean allRejected = false;
			for (final ReceiveCommand cmd : commands) {
				String firstParent = null;
				try {
					final List<RevCommit> commits = JGitUtils.getRevLog(rp.getRepository(), cmd
							.getOldId().name(), cmd.getNewId().name());
					for (final RevCommit commit : commits) {

						if (firstParent != null) {
							if (!commit.getName().equals(firstParent)) {
								// ignore: commit is right-descendant of a merge
								continue;
							}
						}

						// update expected next commit id
						if (commit.getParentCount() == 0) {
							firstParent = null;
						} else {
							firstParent = commit.getParents()[0].getId().getName();
						}

						final PersonIdent committer = commit.getCommitterIdent();
						if (!this.user.is(committer.getName(), committer.getEmailAddress())) {
							// verification failed
							final String reason = MessageFormat.format(
									"{0} by {1} <{2}> was not committed by {3} ({4}) <{5}>", commit
											.getId().name(), committer.getName(),
									StringUtils.isEmpty(committer.getEmailAddress()) ? "?"
											: committer.getEmailAddress(), this.user
											.getDisplayName(), this.user.username,
									this.user.emailAddress);
							LOGGER.warn(reason);
							cmd.setResult(Result.REJECTED_OTHER_REASON, reason);
							allRejected &= true;
							break;
						} else {
							allRejected = false;
						}
					}
				}
				catch (final Exception e) {
					LOGGER.error("Failed to verify commits were made by pushing user", e);
				}
			}

			if (allRejected) {
				// all ref updates rejected, abort
				return;
			}
		}

		for (final ReceiveCommand cmd : commands) {
			final String ref = cmd.getRefName();
			if (ref.startsWith(Constants.R_HEADS)) {
				switch (cmd.getType()) {
				case UPDATE_NONFASTFORWARD:
				case DELETE:
					// reset branch commit cache on REWIND and DELETE
					CommitCache.instance().clear(this.repository.name, ref);
					break;
				default:
					break;
				}
			} else if (ref.equals(BranchTicketService.BRANCH)) {
				// ensure pushing user is an administrator OR an owner
				// i.e. prevent ticket tampering
				final boolean permitted = this.user.canAdmin()
						|| this.repository.isOwner(this.user.username);
				if (!permitted) {
					sendRejection(cmd, "{0} is not permitted to push to {1}", this.user.username,
							ref);
				}
			} else if (ref.startsWith(Constants.R_FOR)) {
				// prevent accidental push to refs/for
				sendRejection(cmd, "{0} is not configured to receive patchsets",
						this.repository.name);
			}
		}

		// call pre-receive plugins
		for (final ReceiveHook hook : this.gitblit.getExtensions(ReceiveHook.class)) {
			try {
				hook.onPreReceive(this, commands);
			}
			catch (final Exception e) {
				LOGGER.error("Failed to execute extension", e);
			}
		}

		final Set<String> scripts = new LinkedHashSet<String>();
		scripts.addAll(this.gitblit.getPreReceiveScriptsInherited(this.repository));
		if (!ArrayUtils.isEmpty(this.repository.preReceiveScripts)) {
			scripts.addAll(this.repository.preReceiveScripts);
		}
		runGroovy(commands, scripts);
		for (final ReceiveCommand cmd : commands) {
			if (!Result.NOT_ATTEMPTED.equals(cmd.getResult())) {
				LOGGER.warn(MessageFormat.format("{0} {1} because \"{2}\"", cmd.getNewId()
						.getName(), cmd.getResult(), cmd.getMessage()));
			}
		}
	}

	/**
	 * Instrumentation point where the incoming push has been applied to the
	 * repository. This is the point where we would trigger a Jenkins build or
	 * send an email.
	 */
	@Override
	public void onPostReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
		if (commands.size() == 0) {
			LOGGER.debug("skipping post-receive processing, no refs created, updated, or removed");
			return;
		}

		logRefChange(commands);
		updateIncrementalPushTags(commands);
		updateGitblitRefLog(commands);

		// check for updates pushed to the BranchTicketService branch
		// if the BranchTicketService is active it will reindex, as appropriate
		for (final ReceiveCommand cmd : commands) {
			if (Result.OK.equals(cmd.getResult())
					&& BranchTicketService.BRANCH.equals(cmd.getRefName())) {
				rp.getRepository().fireEvent(new ReceiveCommandEvent(this.repository, cmd));
			}
		}

		// call post-receive plugins
		for (final ReceiveHook hook : this.gitblit.getExtensions(ReceiveHook.class)) {
			try {
				hook.onPostReceive(this, commands);
			}
			catch (final Exception e) {
				LOGGER.error("Failed to execute extension", e);
			}
		}

		// run Groovy hook scripts
		final Set<String> scripts = new LinkedHashSet<String>();
		scripts.addAll(this.gitblit.getPostReceiveScriptsInherited(this.repository));
		if (!ArrayUtils.isEmpty(this.repository.postReceiveScripts)) {
			scripts.addAll(this.repository.postReceiveScripts);
		}
		runGroovy(commands, scripts);
	}

	/**
	 * Log the ref changes in the container log.
	 *
	 * @param commands
	 */
	protected void logRefChange(Collection<ReceiveCommand> commands) {
		boolean isRefCreationOrDeletion = false;

		// log ref changes
		for (final ReceiveCommand cmd : commands) {

			if (Result.OK.equals(cmd.getResult())) {
				// add some logging for important ref changes
				switch (cmd.getType()) {
				case DELETE:
					LOGGER.info(MessageFormat.format("{0} DELETED {1} in {2} ({3})",
							this.user.username, cmd.getRefName(), this.repository.name, cmd
									.getOldId().name()));
					isRefCreationOrDeletion = true;
					break;
				case CREATE:
					LOGGER.info(MessageFormat.format("{0} CREATED {1} in {2}", this.user.username,
							cmd.getRefName(), this.repository.name));
					isRefCreationOrDeletion = true;
					break;
				case UPDATE:
					LOGGER.info(MessageFormat.format("{0} UPDATED {1} in {2} (from {3} to {4})",
							this.user.username, cmd.getRefName(), this.repository.name, cmd
									.getOldId().name(), cmd.getNewId().name()));
					break;
				case UPDATE_NONFASTFORWARD:
					LOGGER.info(MessageFormat.format(
							"{0} UPDATED NON-FAST-FORWARD {1} in {2} (from {3} to {4})",
							this.user.username, cmd.getRefName(), this.repository.name, cmd
									.getOldId().name(), cmd.getNewId().name()));
					break;
				default:
					break;
				}
			}
		}

		if (isRefCreationOrDeletion) {
			this.gitblit.resetRepositoryCache(this.repository.name);
		}
	}

	/**
	 * Optionally update the incremental push tags.
	 *
	 * @param commands
	 */
	protected void updateIncrementalPushTags(Collection<ReceiveCommand> commands) {
		if (!this.repository.useIncrementalPushTags) {
			return;
		}

		// tag each pushed branch tip
		final String emailAddress = this.user.emailAddress == null ? getRefLogIdent()
				.getEmailAddress() : this.user.emailAddress;
		final PersonIdent userIdent = new PersonIdent(this.user.getDisplayName(), emailAddress);

		for (final ReceiveCommand cmd : commands) {
			if (!cmd.getRefName().startsWith(Constants.R_HEADS)) {
				// only tag branch ref changes
				continue;
			}

			if (!ReceiveCommand.Type.DELETE.equals(cmd.getType())
					&& ReceiveCommand.Result.OK.equals(cmd.getResult())) {
				final String objectId = cmd.getNewId().getName();
				final String branch = cmd.getRefName().substring(Constants.R_HEADS.length());
				// get translation based on the server's locale setting
				final String template = Translation.get("gb.incrementalPushTagMessage");
				final String msg = MessageFormat.format(template, branch);
				String prefix;
				if (StringUtils.isEmpty(this.repository.incrementalPushTagPrefix)) {
					prefix = this.settings.getString(Keys.git.defaultIncrementalPushTagPrefix, "r");
				} else {
					prefix = this.repository.incrementalPushTagPrefix;
				}

				JGitUtils.createIncrementalRevisionTag(getRepository(), objectId, userIdent,
						prefix, "0", msg);
			}
		}
	}

	/**
	 * Update Gitblit's internal reflog.
	 *
	 * @param commands
	 */
	protected void updateGitblitRefLog(Collection<ReceiveCommand> commands) {
		try {
			RefLogUtils.updateRefLog(this.user, getRepository(), commands);
			LOGGER.debug(MessageFormat.format("{0} reflog updated", this.repository.name));
		}
		catch (final Exception e) {
			LOGGER.error(MessageFormat.format("Failed to update {0} reflog", this.repository.name),
					e);
		}
	}

	/** Execute commands to update references. */
	@Override
	protected void executeCommands() {
		final List<ReceiveCommand> toApply = filterCommands(Result.NOT_ATTEMPTED);
		if (toApply.isEmpty()) {
			return;
		}

		ProgressMonitor updating = NullProgressMonitor.INSTANCE;
		final boolean sideBand = isCapabilityEnabled(CAPABILITY_SIDE_BAND_64K);
		if (sideBand) {
			final SideBandProgressMonitor pm = new SideBandProgressMonitor(this.msgOut);
			pm.setDelayStart(250, TimeUnit.MILLISECONDS);
			updating = pm;
		}

		final BatchRefUpdate batch = getRepository().getRefDatabase().newBatchUpdate();
		batch.setAllowNonFastForwards(isAllowNonFastForwards());
		batch.setRefLogIdent(getRefLogIdent());
		batch.setRefLogMessage("push", true);

		for (final ReceiveCommand cmd : toApply) {
			if (Result.NOT_ATTEMPTED != cmd.getResult()) {
				// Already rejected by the core receive process.
				continue;
			}
			batch.addCommand(cmd);
		}

		if (!batch.getCommands().isEmpty()) {
			try {
				batch.execute(getRevWalk(), updating);
			}
			catch (final IOException err) {
				for (final ReceiveCommand cmd : toApply) {
					if (cmd.getResult() == Result.NOT_ATTEMPTED) {
						sendRejection(cmd, "lock error: {0}", err.getMessage());
					}
				}
			}
		}

		//
		// if there are ref update receive commands that were
		// successfully processed and there is an active ticket service for the
		// repository
		// then process any referenced tickets
		//
		if (this.ticketService != null) {
			final List<ReceiveCommand> allUpdates = ReceiveCommand.filter(batch.getCommands(),
					Result.OK);
			if (!allUpdates.isEmpty()) {
				int ticketsProcessed = 0;
				for (final ReceiveCommand cmd : allUpdates) {
					switch (cmd.getType()) {
					case CREATE:
					case UPDATE:
						if (cmd.getRefName().startsWith(Constants.R_HEADS)) {
							final Collection<TicketModel> tickets = processReferencedTickets(cmd);
							ticketsProcessed += tickets.size();
							for (final TicketModel ticket : tickets) {
								this.ticketNotifier.queueMailing(ticket);
							}
						}
						break;

					case UPDATE_NONFASTFORWARD:
						if (cmd.getRefName().startsWith(Constants.R_HEADS)) {
							final String base = JGitUtils.getMergeBase(getRepository(),
									cmd.getOldId(), cmd.getNewId());
							final List<TicketLink> deletedRefs = JGitUtils
									.identifyTicketsBetweenCommits(getRepository(), this.settings,
											base, cmd.getOldId().name());
							for (final TicketLink link : deletedRefs) {
								link.isDelete = true;
							}
							final Change deletion = new Change(this.user.username);
							deletion.pendingLinks = deletedRefs;
							this.ticketService.updateTicket(this.repository, 0, deletion);

							final Collection<TicketModel> tickets = processReferencedTickets(cmd);
							ticketsProcessed += tickets.size();
							for (final TicketModel ticket : tickets) {
								this.ticketNotifier.queueMailing(ticket);
							}
						}
						break;
					case DELETE:
						// Identify if the branch has been merged
						final SortedMap<Integer, String> bases = new TreeMap<Integer, String>();
						try {
							final ObjectId dObj = cmd.getOldId();
							final Collection<Ref> tips = getRepository().getRefDatabase()
									.getRefs(Constants.R_HEADS).values();
							for (final Ref ref : tips) {
								final ObjectId iObj = ref.getObjectId();
								final String mergeBase = JGitUtils.getMergeBase(getRepository(),
										dObj, iObj);
								if (mergeBase != null) {
									final int d = JGitUtils.countCommits(getRepository(),
											getRevWalk(), mergeBase, dObj.name());
									bases.put(d, mergeBase);
									// All commits have been merged into some
									// other branch
									if (d == 0) {
										break;
									}
								}
							}

							if (bases.isEmpty()) {
								// TODO: Handle orphan branch case
							} else {
								if (bases.firstKey() > 0) {
									// Delete references from the remaining
									// commits that haven't been merged
									final String mergeBase = bases.get(bases.firstKey());
									final List<TicketLink> deletedRefs = JGitUtils
											.identifyTicketsBetweenCommits(getRepository(),
													this.settings, mergeBase, dObj.name());

									for (final TicketLink link : deletedRefs) {
										link.isDelete = true;
									}
									final Change deletion = new Change(this.user.username);
									deletion.pendingLinks = deletedRefs;
									this.ticketService.updateTicket(this.repository, 0, deletion);
								}
							}

						}
						catch (final IOException e) {
							LOGGER.error(null, e);
						}
						break;

					default:
						break;
					}
				}

				if (ticketsProcessed == 1) {
					sendInfo("1 ticket updated");
				} else if (ticketsProcessed > 1) {
					sendInfo("{0} tickets updated", ticketsProcessed);
				}
			}

			// reset the ticket caches for the repository
			this.ticketService.resetCaches(this.repository);
		}
	}

	protected void setGitblitUrl(String url) {
		this.gitblitUrl = url;
	}

	public void sendRejection(final ReceiveCommand cmd, final String why, Object... objects) {
		String text;
		if (ArrayUtils.isEmpty(objects)) {
			text = why;
		} else {
			text = MessageFormat.format(why, objects);
		}
		cmd.setResult(Result.REJECTED_OTHER_REASON, text);
		LOGGER.error(text + " (" + this.user.username + ")");
	}

	public void sendHeader(String msg, Object... objects) {
		sendInfo("--> ", msg, objects);
	}

	public void sendInfo(String msg, Object... objects) {
		sendInfo("    ", msg, objects);
	}

	private void sendInfo(String prefix, String msg, Object... objects) {
		String text;
		if (ArrayUtils.isEmpty(objects)) {
			text = msg;
			super.sendMessage(prefix + msg);
		} else {
			text = MessageFormat.format(msg, objects);
			super.sendMessage(prefix + text);
		}
		if (!StringUtils.isEmpty(msg)) {
			LOGGER.info(text + " (" + this.user.username + ")");
		}
	}

	public void sendError(String msg, Object... objects) {
		String text;
		if (ArrayUtils.isEmpty(objects)) {
			text = msg;
			super.sendError(msg);
		} else {
			text = MessageFormat.format(msg, objects);
			super.sendError(text);
		}
		if (!StringUtils.isEmpty(msg)) {
			LOGGER.error(text + " (" + this.user.username + ")");
		}
	}

	/**
	 * Runs the specified Groovy hook scripts.
	 *
	 * @param repository
	 * @param user
	 * @param commands
	 * @param scripts
	 */
	private void runGroovy(Collection<ReceiveCommand> commands, Set<String> scripts) {
		if ((scripts == null) || (scripts.size() == 0)) {
			// no Groovy scripts to execute
			return;
		}

		final Binding binding = new Binding();
		binding.setVariable("gitblit", this.gitblit);
		binding.setVariable("repository", this.repository);
		binding.setVariable("receivePack", this);
		binding.setVariable("user", this.user);
		binding.setVariable("commands", commands);
		binding.setVariable("url", this.gitblitUrl);
		binding.setVariable("logger", LOGGER);
		binding.setVariable("clientLogger", new ClientLogger(this));
		for (String script : scripts) {
			if (StringUtils.isEmpty(script)) {
				continue;
			}
			// allow script to be specified without .groovy extension
			// this is easier to read in the settings
			File file = new File(this.groovyDir, script);
			if (!file.exists() && !script.toLowerCase().endsWith(".groovy")) {
				file = new File(this.groovyDir, script + ".groovy");
				if (file.exists()) {
					script = file.getName();
				}
			}
			try {
				final Object result = this.gse.run(script, binding);
				if (result instanceof Boolean) {
					if (!((Boolean) result)) {
						LOGGER.error(MessageFormat.format(
								"Groovy script {0} has failed!  Hook scripts aborted.", script));
						break;
					}
				}
			}
			catch (final Exception e) {
				LOGGER.error(MessageFormat.format("Failed to execute Groovy script {0}", script), e);
			}
		}
	}

	public IGitblit getGitblit() {
		return this.gitblit;
	}

	public RepositoryModel getRepositoryModel() {
		return this.repository;
	}

	public UserModel getUserModel() {
		return this.user;
	}

	/**
	 * Automatically closes open tickets and adds references to tickets if made
	 * in the commit message.
	 *
	 * @param cmd
	 */
	private Collection<TicketModel> processReferencedTickets(ReceiveCommand cmd) {
		final Map<Long, TicketModel> changedTickets = new LinkedHashMap<Long, TicketModel>();

		final RevWalk rw = getRevWalk();
		try {
			rw.reset();
			rw.markStart(rw.parseCommit(cmd.getNewId()));
			if (!ObjectId.zeroId().equals(cmd.getOldId())) {
				rw.markUninteresting(rw.parseCommit(cmd.getOldId()));
			}

			RevCommit c;
			while ((c = rw.next()) != null) {
				rw.parseBody(c);
				final List<TicketLink> ticketLinks = JGitUtils.identifyTicketsFromCommitMessage(
						getRepository(), this.settings, c);
				if (ticketLinks == null) {
					continue;
				}

				for (final TicketLink link : ticketLinks) {

					TicketModel ticket = this.ticketService.getTicket(this.repository,
							link.targetTicketId);
					if (ticket == null) {
						continue;
					}

					Change change = null;
					final String commitSha = c.getName();
					final String branchName = Repository.shortenRefName(cmd.getRefName());

					switch (link.action) {
					case Commit: {
						// A commit can reference a ticket in any branch even if
						// the ticket is closed.
						// This allows developers to identify and communicate
						// related issues
						change = new Change(this.user.username);
						change.referenceCommit(commitSha);
					}
						break;

					case Close: {
						// As this isn't a patchset theres no merging taking
						// place when closing a ticket
						if (ticket.isClosed()) {
							continue;
						}

						change = new Change(this.user.username);
						change.setField(Field.status, Status.Fixed);

						if (StringUtils.isEmpty(ticket.responsible)) {
							// unassigned tickets are assigned to the closer
							change.setField(Field.responsible, this.user.username);
						}
					}

					//$FALL-THROUGH$
					default: {
						// No action
					}
						break;
					}

					if (change != null) {
						ticket = this.ticketService.updateTicket(this.repository, ticket.number,
								change);
					}

					if (ticket != null) {
						sendInfo("");
						sendHeader("#{0,number,0}: {1}", ticket.number,
								StringUtils.trimString(ticket.title, Constants.LEN_SHORTLOG));

						switch (link.action) {
						case Commit: {
							sendInfo("referenced by push of {0} to {1}", commitSha, branchName);
							changedTickets.put(ticket.number, ticket);
						}
							break;

						case Close: {
							sendInfo("closed by push of {0} to {1}", commitSha, branchName);
							changedTickets.put(ticket.number, ticket);
						}
							break;

						default: {
						}
						}

						sendInfo(this.ticketService.getTicketUrl(ticket));
						sendInfo("");
					} else {
						switch (link.action) {
						case Commit: {
							sendError("FAILED to reference ticket {0} by push of {1}",
									link.targetTicketId, commitSha);
						}
							break;

						case Close: {
							sendError("FAILED to close ticket {0} by push of {1}",
									link.targetTicketId, commitSha);
						}
							break;

						default: {
						}
						}
					}
				}
			}

		}
		catch (final IOException e) {
			LOGGER.error("Can't scan for changes to reference or close", e);
		}
		finally {
			rw.reset();
		}

		return changedTickets.values();
	}
}
