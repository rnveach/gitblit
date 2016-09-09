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
package com.gitblit.wicket.pages;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.protocol.http.RequestUtils;
import org.apache.wicket.request.target.basic.RedirectRequestTarget;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.Keys;
import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.models.RefModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.SubmoduleModel;
import com.gitblit.servlet.RawServlet;
import com.gitblit.utils.DiffUtils;
import com.gitblit.utils.DiffUtils.DiffComparator;
import com.gitblit.utils.DiffUtils.DiffOutput;
import com.gitblit.utils.DiffUtils.DiffOutputType;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.SessionlessForm;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.CommitLegendPanel;
import com.gitblit.wicket.panels.DiffStatPanel;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.LogPanel;

/**
 * The compare page allows you to compare two branches, tags, or hash ids.
 *
 * @author James Moger
 *
 */
public class ComparePage extends RepositoryPage {

	IModel<String> fromCommitId = new Model<String>("");
	IModel<String> toCommitId = new Model<String>("");

	IModel<String> fromRefId = new Model<String>("");
	IModel<String> toRefId = new Model<String>("");

	IModel<Boolean> ignoreWhitespace = Model.of(true);

	public ComparePage(PageParameters params) {
		super(params);
		final Repository r = getRepository();
		final RepositoryModel repository = getRepositoryModel();

		if (StringUtils.isEmpty(this.objectId)) {
			// seleciton form
			add(new Label("comparison").setVisible(false));
		} else {
			// active comparison
			final Fragment comparison = new Fragment("comparison", "comparisonFragment", this);
			add(comparison);

			RevCommit fromCommit;
			RevCommit toCommit;

			final String[] parts = this.objectId.split("\\.\\.");
			if (parts[0].startsWith("refs/") && parts[1].startsWith("refs/")) {
				// set the ref models
				this.fromRefId.setObject(parts[0]);
				this.toRefId.setObject(parts[1]);

				fromCommit = getCommit(r, this.fromRefId.getObject());
				toCommit = getCommit(r, this.toRefId.getObject());
			} else {
				// set the id models
				this.fromCommitId.setObject(parts[0]);
				this.toCommitId.setObject(parts[1]);

				fromCommit = getCommit(r, this.fromCommitId.getObject());
				toCommit = getCommit(r, this.toCommitId.getObject());
			}

			// prepare submodules
			getSubmodules(toCommit);

			final String startId = fromCommit.getId().getName();
			final String endId = toCommit.getId().getName();

			// commit ids
			this.fromCommitId.setObject(startId);
			this.toCommitId.setObject(endId);

			final List<String> imageExtensions = app().settings().getStrings(
					Keys.web.imageExtensions);
			final ImageDiffHandler handler = new ImageDiffHandler(this, this.repositoryName,
					fromCommit.getName(), toCommit.getName(), imageExtensions);
			final DiffComparator diffComparator = WicketUtils.getDiffComparator(params);
			final int tabLength = app().settings().getInteger(Keys.web.tabLength, 4);
			final DiffOutput diff = DiffUtils.getDiff(r, fromCommit, toCommit, diffComparator,
					DiffOutputType.HTML, handler, tabLength);
			if (handler.getImgDiffCount() > 0) {
				addBottomScript("scripts/imgdiff.js"); // Tiny support script
														// for image diffs
			}

			// add compare diffstat
			int insertions = 0;
			int deletions = 0;
			for (final PathChangeModel pcm : diff.stat.paths) {
				insertions += pcm.insertions;
				deletions += pcm.deletions;
			}
			comparison.add(new DiffStatPanel("diffStat", insertions, deletions));

			// compare page links
			// comparison.add(new BookmarkablePageLink<Void>("patchLink",
			// PatchPage.class,
			// WicketUtils.newRangeParameter(repositoryName,
			// fromCommitId.toString(), toCommitId.getObject())));

			// display list of commits
			comparison.add(new LogPanel("commitList", this.repositoryName, this.objectId, r, 0, 0,
					repository.showRemoteBranches));

			// changed paths list
			comparison.add(new CommitLegendPanel("commitLegend", diff.stat.paths));
			final ListDataProvider<PathChangeModel> pathsDp = new ListDataProvider<PathChangeModel>(
					diff.stat.paths);
			final DataView<PathChangeModel> pathsView = new DataView<PathChangeModel>(
					"changedPath", pathsDp) {
				private static final long serialVersionUID = 1L;
				int counter;

				@Override
				public void populateItem(final Item<PathChangeModel> item) {
					final PathChangeModel entry = item.getModelObject();
					final Label changeType = new Label("changeType", "");
					WicketUtils.setChangeTypeCssClass(changeType, entry.changeType);
					setChangeTypeTooltip(changeType, entry.changeType);
					item.add(changeType);
					item.add(new DiffStatPanel("diffStat", entry.insertions, entry.deletions, true));

					boolean hasSubmodule = false;
					String submodulePath = null;
					if (entry.isTree()) {
						// tree
						item.add(new LinkPanel("pathName", null, entry.path, TreePage.class,
								WicketUtils.newPathParameter(ComparePage.this.repositoryName,
										endId, entry.path)));
					} else if (entry.isSubmodule()) {
						// submodule
						final String submoduleId = entry.objectId;
						final SubmoduleModel submodule = getSubmodule(entry.path);
						submodulePath = submodule.gitblitPath;
						hasSubmodule = submodule.hasSubmodule;

						// add relative link
						item.add(new LinkPanel("pathName", "list", entry.path + " @ "
								+ getShortObjectId(submoduleId), "#n" + entry.objectId));
					} else {
						// add relative link
						item.add(new LinkPanel("pathName", "list", entry.path, "#n"
								+ entry.objectId));
					}

					// quick links
					if (entry.isSubmodule()) {
						// submodule
						item.add(new ExternalLink("patch", "").setEnabled(false));
						item.add(new BookmarkablePageLink<Void>("view", CommitPage.class,
								WicketUtils.newObjectParameter(submodulePath, entry.objectId))
								.setEnabled(hasSubmodule));
						item.add(new ExternalLink("raw", "").setEnabled(false));
						item.add(new ExternalLink("blame", "").setEnabled(false));
						item.add(new BookmarkablePageLink<Void>("history", HistoryPage.class,
								WicketUtils.newPathParameter(ComparePage.this.repositoryName,
										endId, entry.path)).setEnabled(!entry.changeType
								.equals(ChangeType.ADD)));
					} else {
						// tree or blob
						item.add(new BookmarkablePageLink<Void>("patch", PatchPage.class,
								WicketUtils.newBlobDiffParameter(ComparePage.this.repositoryName,
										startId, endId, entry.path)).setEnabled(!entry.changeType
								.equals(ChangeType.DELETE)));
						item.add(new BookmarkablePageLink<Void>("view", BlobPage.class, WicketUtils
								.newPathParameter(ComparePage.this.repositoryName, endId,
										entry.path)).setEnabled(!entry.changeType
								.equals(ChangeType.DELETE)));
						final String rawUrl = RawServlet.asLink(getContextUrl(),
								ComparePage.this.repositoryName, endId, entry.path);
						item.add(new ExternalLink("raw", rawUrl).setEnabled(!entry.changeType
								.equals(ChangeType.DELETE)));
						item.add(new BookmarkablePageLink<Void>("blame", BlamePage.class,
								WicketUtils.newPathParameter(ComparePage.this.repositoryName,
										endId, entry.path)).setEnabled(!entry.changeType
								.equals(ChangeType.ADD)
								&& !entry.changeType.equals(ChangeType.DELETE)));
						item.add(new BookmarkablePageLink<Void>("history", HistoryPage.class,
								WicketUtils.newPathParameter(ComparePage.this.repositoryName,
										endId, entry.path)).setEnabled(!entry.changeType
								.equals(ChangeType.ADD)));
					}
					WicketUtils.setAlternatingBackground(item, this.counter);
					this.counter++;
				}
			};
			comparison.add(pathsView);
			comparison.add(new Label("diffText", diff.content).setEscapeModelStrings(false));
		}

		// set the default DiffComparator
		final DiffComparator diffComparator = WicketUtils.getDiffComparator(params);
		this.ignoreWhitespace.setObject(DiffComparator.IGNORE_WHITESPACE == diffComparator);

		//
		// ref selection form
		//
		final SessionlessForm<Void> refsForm = new SessionlessForm<Void>("compareRefsForm",
				getClass(), getPageParameters()) {

			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
				final String from = ComparePage.this.fromRefId.getObject();
				final String to = ComparePage.this.toRefId.getObject();
				final boolean ignoreWS = ComparePage.this.ignoreWhitespace.getObject();

				final PageParameters params = WicketUtils.newRangeParameter(
						ComparePage.this.repositoryName, from, to);
				if (ignoreWS) {
					params.put("w", 1);
				}

				final String relativeUrl = urlFor(ComparePage.class, params).toString();
				final String absoluteUrl = RequestUtils.toAbsolutePath(relativeUrl);
				getRequestCycle().setRequestTarget(new RedirectRequestTarget(absoluteUrl));
			}
		};

		final List<String> refs = new ArrayList<String>();
		for (final RefModel ref : JGitUtils.getLocalBranches(r, true, -1)) {
			refs.add(ref.getName());
		}
		if (repository.showRemoteBranches) {
			for (final RefModel ref : JGitUtils.getRemoteBranches(r, true, -1)) {
				refs.add(ref.getName());
			}
		}
		for (final RefModel ref : JGitUtils.getTags(r, true, -1)) {
			refs.add(ref.getName());
		}
		refsForm.add(new DropDownChoice<String>("fromRef", this.fromRefId, refs).setEnabled(refs
				.size() > 0));
		refsForm.add(new DropDownChoice<String>("toRef", this.toRefId, refs).setEnabled(refs.size() > 0));
		refsForm.add(new Label("ignoreWhitespaceLabel", getString(DiffComparator.IGNORE_WHITESPACE
				.getTranslationKey())));
		refsForm.add(new CheckBox("ignoreWhitespaceCheckbox", this.ignoreWhitespace));
		add(refsForm);

		//
		// manual ids form
		//
		final SessionlessForm<Void> idsForm = new SessionlessForm<Void>("compareIdsForm",
				getClass(), getPageParameters()) {

			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
				final String from = ComparePage.this.fromCommitId.getObject();
				final String to = ComparePage.this.toCommitId.getObject();
				final boolean ignoreWS = ComparePage.this.ignoreWhitespace.getObject();

				final PageParameters params = WicketUtils.newRangeParameter(
						ComparePage.this.repositoryName, from, to);
				if (ignoreWS) {
					params.put("w", 1);
				}
				final String relativeUrl = urlFor(ComparePage.class, params).toString();
				final String absoluteUrl = RequestUtils.toAbsolutePath(relativeUrl);
				getRequestCycle().setRequestTarget(new RedirectRequestTarget(absoluteUrl));
			}
		};

		final TextField<String> fromIdField = new TextField<String>("fromId", this.fromCommitId);
		WicketUtils.setInputPlaceholder(fromIdField, getString("gb.from") + "...");
		idsForm.add(fromIdField);

		final TextField<String> toIdField = new TextField<String>("toId", this.toCommitId);
		WicketUtils.setInputPlaceholder(toIdField, getString("gb.to") + "...");
		idsForm.add(toIdField);
		idsForm.add(new Label("ignoreWhitespaceLabel", getString(DiffComparator.IGNORE_WHITESPACE
				.getTranslationKey())));
		idsForm.add(new CheckBox("ignoreWhitespaceCheckbox", this.ignoreWhitespace));
		add(idsForm);

		r.close();
	}

	@Override
	protected String getPageName() {
		return getString("gb.compare");
	}

	@Override
	protected Class<? extends BasePage> getRepoNavPageClass() {
		return ComparePage.class;
	}

	private RevCommit getCommit(Repository r, String rev) {
		final RevCommit otherCommit = JGitUtils.getCommit(r, rev);
		if (otherCommit == null) {
			error(MessageFormat.format(getString("gb.failedToFindCommit"), rev,
					this.repositoryName, getPageName()), true);
		}
		return otherCommit;
	}
}
