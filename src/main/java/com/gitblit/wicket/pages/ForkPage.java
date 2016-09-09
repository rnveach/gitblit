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
package com.gitblit.wicket.pages;

import java.text.MessageFormat;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.slf4j.LoggerFactory;

import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.GitblitRedirectException;
import com.gitblit.wicket.WicketUtils;

public class ForkPage extends RepositoryPage {

	public ForkPage(PageParameters params) {
		super(params);

		setVersioned(false);

		final GitBlitWebSession session = GitBlitWebSession.get();

		final RepositoryModel repository = getRepositoryModel();
		final UserModel user = session.getUser();
		final boolean canFork = user.canFork(repository);

		if (!canFork) {
			// redirect to the summary page if this repository is not empty
			GitBlitWebSession.get().cacheErrorMessage(
					MessageFormat.format(getString("gb.forkNotAuthorized"), repository.name));
			throw new GitblitRedirectException(SummaryPage.class,
					WicketUtils.newRepositoryParameter(repository.name));
		}

		final String fork = app().repositories().getFork(user.username, repository.name);
		if (fork != null) {
			// redirect to user's fork
			throw new GitblitRedirectException(SummaryPage.class,
					WicketUtils.newRepositoryParameter(fork));
		}

		add(new Label("forkText", getString("gb.preparingFork")));

		if (!session.isForking()) {
			// prepare session
			session.isForking(true);

			// fork it
			final ForkThread forker = new ForkThread(app(), repository, session);
			forker.start();
		}
	}

	@Override
	protected boolean allowForkControls() {
		return false;
	}

	@Override
	protected String getPageName() {
		return "fork";
	}

	/**
	 * ForkThread does the work of working the repository in a background
	 * thread. The completion status is tracked through a session variable and
	 * monitored by this page.
	 */
	private static class ForkThread extends Thread {

		private final GitBlitWebApp app;
		private final RepositoryModel repository;
		private final GitBlitWebSession session;

		public ForkThread(GitBlitWebApp app, RepositoryModel repository, GitBlitWebSession session) {
			this.app = app;
			this.repository = repository;
			this.session = session;
		}

		@Override
		public void run() {
			final UserModel user = this.session.getUser();
			try {
				this.app.gitblit().fork(this.repository, user);
			}
			catch (final Exception e) {
				LoggerFactory.getLogger(ForkPage.class).error(
						MessageFormat.format("Failed to fork {0} for {1}", this.repository.name,
								user.username), e);
			}
			finally {
				this.session.isForking(false);
			}
		}
	}
}
