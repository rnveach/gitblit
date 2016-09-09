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
package com.gitblit.service;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Type;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.git.ReceiveCommandEvent;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.BranchTicketService;
import com.gitblit.utils.JGitUtils;

/**
 * The Mirror service handles periodic fetching of mirrored repositories.
 *
 * @author James Moger
 *
 */
public class MirrorService implements Runnable {

	private final Logger logger = LoggerFactory.getLogger(MirrorService.class);

	private final Set<String> repairAttempted = Collections.synchronizedSet(new HashSet<String>());

	private final IStoredSettings settings;

	private final IRepositoryManager repositoryManager;

	private final AtomicBoolean running = new AtomicBoolean(false);

	private final AtomicBoolean forceClose = new AtomicBoolean(false);

	private final UserModel gitblitUser;

	public MirrorService(IStoredSettings settings, IRepositoryManager repositoryManager) {

		this.settings = settings;
		this.repositoryManager = repositoryManager;
		this.gitblitUser = new UserModel("gitblit");
		this.gitblitUser.displayName = "Gitblit";
	}

	public boolean isReady() {
		return this.settings.getBoolean(Keys.git.enableMirroring, false);
	}

	public boolean isRunning() {
		return this.running.get();
	}

	public void close() {
		this.forceClose.set(true);
	}

	@Override
	public void run() {
		if (!isReady()) {
			return;
		}

		this.running.set(true);

		for (final String repositoryName : this.repositoryManager.getRepositoryList()) {
			if (this.forceClose.get()) {
				break;
			}
			if (this.repositoryManager.isCollectingGarbage(repositoryName)) {
				this.logger.debug("mirror is skipping {} garbagecollection", repositoryName);
				continue;
			}
			RepositoryModel model = null;
			Repository repository = null;
			try {
				model = this.repositoryManager.getRepositoryModel(repositoryName);
				if (!model.isMirror && !model.isBare) {
					// repository must be a valid bare git mirror
					this.logger.debug("mirror is skipping {} !mirror !bare", repositoryName);
					continue;
				}

				repository = this.repositoryManager.getRepository(repositoryName);
				if (repository == null) {
					this.logger.warn(MessageFormat.format(
							"MirrorExecutor is missing repository {0}?!?", repositoryName));
					continue;
				}

				// automatically repair (some) invalid fetch ref specs
				if (!this.repairAttempted.contains(repositoryName)) {
					this.repairAttempted.add(repositoryName);
					JGitUtils.repairFetchSpecs(repository);
				}

				// find the first mirror remote - there should only be one
				final StoredConfig rc = repository.getConfig();
				RemoteConfig mirror = null;
				final List<RemoteConfig> configs = RemoteConfig.getAllRemoteConfigs(rc);
				for (final RemoteConfig config : configs) {
					if (config.isMirror()) {
						mirror = config;
						break;
					}
				}

				if (mirror == null) {
					// repository does not have a mirror remote
					this.logger.debug("mirror is skipping {} no mirror remote found",
							repositoryName);
					continue;
				}

				this.logger.debug("checking {} remote {} for ref updates", repositoryName,
						mirror.getName());
				final boolean testing = false;
				final Git git = new Git(repository);
				final FetchResult result = git.fetch().setRemote(mirror.getName())
						.setDryRun(testing).call();
				git.close();
				final Collection<TrackingRefUpdate> refUpdates = result.getTrackingRefUpdates();
				if (refUpdates.size() > 0) {
					ReceiveCommand ticketBranchCmd = null;
					for (final TrackingRefUpdate ru : refUpdates) {
						final StringBuilder sb = new StringBuilder();
						sb.append("updated mirror ");
						sb.append(repositoryName);
						sb.append(" ");
						sb.append(ru.getRemoteName());
						sb.append(" -> ");
						sb.append(ru.getLocalName());
						if (ru.getResult() == Result.FORCED) {
							sb.append(" (forced)");
						}
						sb.append(" ");
						sb.append(ru.getOldObjectId() == null ? "" : ru.getOldObjectId()
								.abbreviate(7).name());
						sb.append("..");
						sb.append(ru.getNewObjectId() == null ? "" : ru.getNewObjectId()
								.abbreviate(7).name());
						this.logger.info(sb.toString());

						if (BranchTicketService.BRANCH.equals(ru.getLocalName())) {
							final ReceiveCommand.Type type;
							switch (ru.getResult()) {
							case NEW:
								type = Type.CREATE;
								break;
							case FAST_FORWARD:
								type = Type.UPDATE;
								break;
							case FORCED:
								type = Type.UPDATE_NONFASTFORWARD;
								break;
							default:
								type = null;
								break;
							}

							if (type != null) {
								ticketBranchCmd = new ReceiveCommand(ru.getOldObjectId(),
										ru.getNewObjectId(), ru.getLocalName(), type);
							}
						}
					}

					if (ticketBranchCmd != null) {
						repository.fireEvent(new ReceiveCommandEvent(model, ticketBranchCmd));
					}
				}
			}
			catch (final Exception e) {
				this.logger.error("Error updating mirror " + repositoryName, e);
			}
			finally {
				// cleanup
				if (repository != null) {
					repository.close();
				}
			}
		}

		this.running.set(false);
	}
}
