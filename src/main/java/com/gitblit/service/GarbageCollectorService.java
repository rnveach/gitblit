/*
 * Copyright 2012 gitblit.com.
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
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.api.GarbageCollectCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.FileUtils;

/**
 * The Garbage Collector Service handles periodic garbage collection in
 * repositories.
 *
 * @author James Moger
 *
 */
public class GarbageCollectorService implements Runnable {

	public static enum GCStatus {
		READY,
		COLLECTING;

		public boolean exceeds(GCStatus s) {
			return ordinal() > s.ordinal();
		}
	}

	private final Logger logger = LoggerFactory.getLogger(GarbageCollectorService.class);

	private final IStoredSettings settings;

	private final IRepositoryManager repositoryManager;

	private final AtomicBoolean running = new AtomicBoolean(false);

	private final AtomicBoolean forceClose = new AtomicBoolean(false);

	private final Map<String, GCStatus> gcCache = new ConcurrentHashMap<String, GCStatus>();

	public GarbageCollectorService(IStoredSettings settings, IRepositoryManager repositoryManager) {

		this.settings = settings;
		this.repositoryManager = repositoryManager;
	}

	/**
	 * Indicates if the GC executor is ready to process repositories.
	 *
	 * @return true if the GC executor is ready to process repositories
	 */
	public boolean isReady() {
		return this.settings.getBoolean(Keys.git.enableGarbageCollection, false);
	}

	public boolean isRunning() {
		return this.running.get();
	}

	public boolean lock(String repositoryName) {
		return setGCStatus(repositoryName, GCStatus.COLLECTING);
	}

	/**
	 * Tries to set a GCStatus for the specified repository.
	 *
	 * @param repositoryName
	 * @return true if the status has been set
	 */
	private boolean setGCStatus(String repositoryName, GCStatus status) {
		final String key = repositoryName.toLowerCase();
		if (this.gcCache.containsKey(key)) {
			if (this.gcCache.get(key).exceeds(GCStatus.READY)) {
				// already collecting or blocked
				return false;
			}
		}
		this.gcCache.put(key, status);
		return true;
	}

	/**
	 * Returns true if Gitblit is actively collecting garbage in this
	 * repository.
	 *
	 * @param repositoryName
	 * @return true if actively collecting garbage
	 */
	public boolean isCollectingGarbage(String repositoryName) {
		final String key = repositoryName.toLowerCase();
		return this.gcCache.containsKey(key) && GCStatus.COLLECTING.equals(this.gcCache.get(key));
	}

	/**
	 * Resets the GC status to ready.
	 *
	 * @param repositoryName
	 */
	public void releaseLock(String repositoryName) {
		this.gcCache.put(repositoryName.toLowerCase(), GCStatus.READY);
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
		final Date now = new Date();

		for (final String repositoryName : this.repositoryManager.getRepositoryList()) {
			if (this.forceClose.get()) {
				break;
			}
			if (isCollectingGarbage(repositoryName)) {
				this.logger.warn(MessageFormat.format("Already collecting garbage from {0}?!?",
						repositoryName));
				continue;
			}
			boolean garbageCollected = false;
			RepositoryModel model = null;
			Repository repository = null;
			try {
				model = this.repositoryManager.getRepositoryModel(repositoryName);
				repository = this.repositoryManager.getRepository(repositoryName);
				if (repository == null) {
					this.logger.warn(MessageFormat.format(
							"GCExecutor is missing repository {0}?!?", repositoryName));
					continue;
				}

				if (!this.repositoryManager.isIdle(repository)) {
					this.logger.debug(MessageFormat.format(
							"GCExecutor is skipping {0} because it is not idle", repositoryName));
					continue;
				}

				// By setting the GCStatus to COLLECTING we are
				// disabling *all* access to this repository from Gitblit.
				// Think of this as a clutch in a manual transmission vehicle.
				if (!setGCStatus(repositoryName, GCStatus.COLLECTING)) {
					this.logger.warn(MessageFormat.format(
							"Can not acquire GC lock for {0}, skipping", repositoryName));
					continue;
				}

				this.logger.debug(MessageFormat.format("GCExecutor locked idle repository {0}",
						repositoryName));

				final Git git = new Git(repository);
				final GarbageCollectCommand gc = git.gc();
				final Properties stats = gc.getStatistics();

				// determine if this is a scheduled GC
				final Calendar cal = Calendar.getInstance();
				cal.setTime(model.lastGC);
				cal.set(Calendar.HOUR_OF_DAY, 0);
				cal.set(Calendar.MINUTE, 0);
				cal.set(Calendar.SECOND, 0);
				cal.set(Calendar.MILLISECOND, 0);
				cal.add(Calendar.DATE, model.gcPeriod);
				final Date gcDate = cal.getTime();
				final boolean shouldCollectGarbage = now.after(gcDate);

				// determine if filesize triggered GC
				final long gcThreshold = FileUtils
						.convertSizeToLong(model.gcThreshold, 500 * 1024L);
				final long sizeOfLooseObjects = (Long) stats.get("sizeOfLooseObjects");
				final boolean hasEnoughGarbage = sizeOfLooseObjects >= gcThreshold;

				// if we satisfy one of the requirements, GC
				final boolean hasGarbage = sizeOfLooseObjects > 0;
				if (hasGarbage && (hasEnoughGarbage || shouldCollectGarbage)) {
					final long looseKB = sizeOfLooseObjects / 1024L;
					this.logger
							.info(MessageFormat.format(
									"Collecting {1} KB of loose objects from {0}", repositoryName,
									looseKB));

					// do the deed
					gc.call();

					garbageCollected = true;
				}
				git.close();
			}
			catch (final Exception e) {
				this.logger.error("Error collecting garbage in " + repositoryName, e);
			}
			finally {
				// cleanup
				if (repository != null) {
					if (garbageCollected) {
						// update the last GC date
						model.lastGC = new Date();
						this.repositoryManager.updateConfiguration(repository, model);
					}

					repository.close();
				}

				// reset the GC lock
				releaseLock(repositoryName);
				this.logger.debug(MessageFormat.format("GCExecutor released GC lock for {0}",
						repositoryName));
			}
		}

		this.running.set(false);
	}
}
