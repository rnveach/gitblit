/*
 * Copyright 2011 gitblit.com.
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

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;

import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.CacheControl.LastModified;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.HistoryPanel;

@CacheControl(LastModified.REPOSITORY)
public class HistoryPage extends RepositoryPage {

	public HistoryPage(PageParameters params) {
		super(params);

		final String path = WicketUtils.getPath(params);
		final int pageNumber = WicketUtils.getPage(params);
		final int prevPage = Math.max(0, pageNumber - 1);
		final int nextPage = pageNumber + 1;

		final HistoryPanel history = new HistoryPanel("historyPanel", this.repositoryName,
				this.objectId, path, getRepository(), -1, pageNumber - 1,
				getRepositoryModel().showRemoteBranches);
		final boolean hasMore = history.hasMore();
		add(history);

		add(new BookmarkablePageLink<Void>("firstPageTop", HistoryPage.class,
				WicketUtils.newPathParameter(this.repositoryName, this.objectId, path))
				.setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("prevPageTop", HistoryPage.class,
				WicketUtils.newHistoryPageParameter(this.repositoryName, this.objectId, path,
						prevPage)).setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("nextPageTop", HistoryPage.class,
				WicketUtils.newHistoryPageParameter(this.repositoryName, this.objectId, path,
						nextPage)).setEnabled(hasMore));

		add(new BookmarkablePageLink<Void>("firstPageBottom", HistoryPage.class,
				WicketUtils.newPathParameter(this.repositoryName, this.objectId, path))
				.setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("prevPageBottom", HistoryPage.class,
				WicketUtils.newHistoryPageParameter(this.repositoryName, this.objectId, path,
						prevPage)).setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("nextPageBottom", HistoryPage.class,
				WicketUtils.newHistoryPageParameter(this.repositoryName, this.objectId, path,
						nextPage)).setEnabled(hasMore));

	}

	@Override
	protected String getPageName() {
		return getString("gb.history");
	}

	@Override
	protected boolean isCommitPage() {
		return true;
	}

	@Override
	protected Class<? extends BasePage> getRepoNavPageClass() {
		return TreePage.class;
	}
}
