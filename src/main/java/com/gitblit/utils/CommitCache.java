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
package com.gitblit.utils;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.RefModel;
import com.gitblit.models.RepositoryCommit;

/**
 * Caches repository commits for re-use in the dashboard and activity pages.
 *
 * @author James Moger
 *
 */
public class CommitCache {

	private static final CommitCache instance;

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	protected final Map<String, ObjectCache<List<RepositoryCommit>>> cache;

	protected int cacheDays = -1;

	public static CommitCache instance() {
		return instance;
	}

	static {
		instance = new CommitCache();
	}

	protected CommitCache() {
		this.cache = new HashMap<>();
	}

	/**
	 * Returns the cutoff date for the cache. Commits after this date are
	 * cached. Commits before this date are not cached.
	 *
	 * @return
	 */
	public Date getCutoffDate() {
		final Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(System.currentTimeMillis());
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		cal.add(Calendar.DATE, -1 * this.cacheDays);
		return cal.getTime();
	}

	/**
	 * Sets the number of days to cache.
	 *
	 * @param days
	 */
	public synchronized void setCacheDays(int days) {
		this.cacheDays = days;
		clear();
	}

	/**
	 * Clears the entire commit cache.
	 *
	 */
	public void clear() {
		synchronized (this.cache) {
			this.cache.clear();
		}
	}

	/**
	 * Clears the commit cache for a specific repository.
	 *
	 * @param repositoryName
	 */
	public void clear(String repositoryName) {
		final String repoKey = repositoryName.toLowerCase();
		boolean hadEntries = false;
		synchronized (this.cache) {
			hadEntries = this.cache.remove(repoKey) != null;
		}
		if (hadEntries) {
			this.logger.info(MessageFormat.format("{0} commit cache cleared", repositoryName));
		}
	}

	/**
	 * Clears the commit cache for a specific branch of a specific repository.
	 *
	 * @param repositoryName
	 * @param branch
	 */
	public void clear(String repositoryName, String branch) {
		final String repoKey = repositoryName.toLowerCase();
		boolean hadEntries = false;
		synchronized (this.cache) {
			final ObjectCache<List<RepositoryCommit>> repoCache = this.cache.get(repoKey);
			if (repoCache != null) {
				final List<RepositoryCommit> commits = repoCache.remove(branch.toLowerCase());
				hadEntries = !ArrayUtils.isEmpty(commits);
			}
		}
		if (hadEntries) {
			this.logger.info(
					MessageFormat.format("{0}:{1} commit cache cleared", repositoryName, branch));
		}
	}

	/**
	 * Get all commits for the specified repository:branch that are in the
	 * cache.
	 *
	 * @param repositoryName
	 * @param repository
	 * @param branch
	 * @return a list of commits
	 */
	public List<RepositoryCommit> getCommits(String repositoryName, Repository repository,
			String branch) {
		return getCommits(repositoryName, repository, branch, getCutoffDate());
	}

	/**
	 * Get all commits for the specified repository:branch since a specific
	 * date. These commits may be retrieved from the cache if the sinceDate is
	 * after the cacheCutoffDate.
	 *
	 * @param repositoryName
	 * @param repository
	 * @param branch
	 * @param sinceDate
	 * @return a list of commits
	 */
	public List<RepositoryCommit> getCommits(String repositoryName, Repository repository,
			String branch, Date sinceDate) {
		final long start = System.nanoTime();
		final Date cacheCutoffDate = getCutoffDate();
		List<RepositoryCommit> list;
		if ((this.cacheDays > 0) && (sinceDate.getTime() >= cacheCutoffDate.getTime())) {
			// request fits within the cache window
			final String repoKey = repositoryName.toLowerCase();
			final String branchKey = branch.toLowerCase();

			final RevCommit tip = JGitUtils.getCommit(repository, branch);
			final Date tipDate = JGitUtils.getCommitDate(tip);

			ObjectCache<List<RepositoryCommit>> repoCache;
			synchronized (this.cache) {
				repoCache = this.cache.get(repoKey);
				if (repoCache == null) {
					repoCache = new ObjectCache<>();
					this.cache.put(repoKey, repoCache);
				}
			}
			synchronized (repoCache) {
				List<RepositoryCommit> commits;
				if (!repoCache.hasCurrent(branchKey, tipDate)) {
					commits = repoCache.getObject(branchKey);
					if (ArrayUtils.isEmpty(commits)) {
						// we don't have any cached commits for this branch,
						// reload
						commits = get(repositoryName, repository, branch, cacheCutoffDate);
						repoCache.updateObject(branchKey, tipDate, commits);
						this.logger.debug(MessageFormat.format(
								"parsed {0} commits from {1}:{2} since {3,date,yyyy-MM-dd} in {4} msecs",
								commits.size(), repositoryName, branch, cacheCutoffDate,
								TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
					} else {
						// incrementally update cache since the last cached
						// commit
						final ObjectId sinceCommit = commits.get(0).getId();
						final List<RepositoryCommit> incremental = get(repositoryName, repository,
								branch, sinceCommit);
						this.logger.info(MessageFormat.format(
								"incrementally added {0} commits to cache for {1}:{2} in {3} msecs",
								incremental.size(), repositoryName, branch,
								TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
						incremental.addAll(commits);
						repoCache.updateObject(branchKey, tipDate, incremental);
						commits = incremental;
					}
				} else {
					// cache is current
					commits = repoCache.getObject(branchKey);
					// evict older commits outside the cache window
					commits = reduce(commits, cacheCutoffDate);
					// update cache
					repoCache.updateObject(branchKey, tipDate, commits);
				}

				if (sinceDate.equals(cacheCutoffDate)) {
					// Mustn't hand out the cached list; that's not thread-safe
					list = new ArrayList<>(commits);
				} else {
					// reduce the commits to those since the specified date
					list = reduce(commits, sinceDate);
				}
			}
			this.logger.debug(MessageFormat.format(
					"retrieved {0} commits from cache of {1}:{2} since {3,date,yyyy-MM-dd} in {4} msecs",
					list.size(), repositoryName, branch, sinceDate,
					TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
		} else {
			// not caching or request outside cache window
			list = get(repositoryName, repository, branch, sinceDate);
			this.logger.debug(MessageFormat.format(
					"parsed {0} commits from {1}:{2} since {3,date,yyyy-MM-dd} in {4} msecs",
					list.size(), repositoryName, branch, sinceDate,
					TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
		}
		return list;
	}

	/**
	 * Returns a list of commits for the specified repository branch.
	 *
	 * @param repositoryName
	 * @param repository
	 * @param branch
	 * @param sinceDate
	 * @return a list of commits
	 */
	protected List<RepositoryCommit> get(String repositoryName, Repository repository,
			String branch, Date sinceDate) {
		final Map<ObjectId, List<RefModel>> allRefs = JGitUtils.getAllRefs(repository, false);
		final List<RevCommit> revLog = JGitUtils.getRevLog(repository, branch, sinceDate);
		final List<RepositoryCommit> commits = new ArrayList<RepositoryCommit>(revLog.size());
		for (final RevCommit commit : revLog) {
			final RepositoryCommit commitModel = new RepositoryCommit(repositoryName, branch,
					commit);
			final List<RefModel> commitRefs = allRefs.get(commitModel.getId());
			commitModel.setRefs(commitRefs);
			commits.add(commitModel);
		}
		return commits;
	}

	/**
	 * Returns a list of commits for the specified repository branch since the
	 * specified commit.
	 *
	 * @param repositoryName
	 * @param repository
	 * @param branch
	 * @param sinceCommit
	 * @return a list of commits
	 */
	protected List<RepositoryCommit> get(String repositoryName, Repository repository,
			String branch, ObjectId sinceCommit) {
		final Map<ObjectId, List<RefModel>> allRefs = JGitUtils.getAllRefs(repository, false);
		final List<RevCommit> revLog = JGitUtils.getRevLog(repository, sinceCommit.getName(),
				branch);
		final List<RepositoryCommit> commits = new ArrayList<RepositoryCommit>(revLog.size());
		for (final RevCommit commit : revLog) {
			final RepositoryCommit commitModel = new RepositoryCommit(repositoryName, branch,
					commit);
			final List<RefModel> commitRefs = allRefs.get(commitModel.getId());
			commitModel.setRefs(commitRefs);
			commits.add(commitModel);
		}
		return commits;
	}

	/**
	 * Reduces the list of commits to those since the specified date.
	 *
	 * @param commits
	 * @param sinceDate
	 * @return a list of commits
	 */
	protected List<RepositoryCommit> reduce(List<RepositoryCommit> commits, Date sinceDate) {
		final List<RepositoryCommit> filtered = new ArrayList<RepositoryCommit>(commits.size());
		for (final RepositoryCommit commit : commits) {
			if (commit.getCommitDate().compareTo(sinceDate) >= 0) {
				filtered.add(commit);
			}
		}
		return filtered;
	}
}
