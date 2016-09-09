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

import java.awt.Color;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.Keys;
import com.gitblit.models.AnnotatedLine;
import com.gitblit.models.PathModel;
import com.gitblit.utils.ColorFactory;
import com.gitblit.utils.DiffUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.CacheControl.LastModified;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.CommitHeaderPanel;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.PathBreadcrumbsPanel;

@CacheControl(LastModified.BOOT)
public class BlamePage extends RepositoryPage {

	/**
	 * The different types of Blame visualizations.
	 */
	private enum BlameType {
		COMMIT,

		AUTHOR,

		AGE;

		private BlameType() {
		}

		public static BlameType get(String name) {
			for (final BlameType blameType : BlameType.values()) {
				if (blameType.name().equalsIgnoreCase(name)) {
					return blameType;
				}
			}
			throw new IllegalArgumentException("Unknown Blame Type [" + name + "]");
		}

		@Override
		public String toString() {
			return name().toLowerCase();
		}
	}

	public BlamePage(PageParameters params) {
		super(params);

		final String blobPath = WicketUtils.getPath(params);

		final String blameTypeParam = params.getString("blametype", BlameType.COMMIT.toString());
		final BlameType activeBlameType = BlameType.get(blameTypeParam);

		final RevCommit commit = getCommit();

		PathModel pathModel = null;

		final List<PathModel> paths = JGitUtils.getFilesInPath(getRepository(),
				StringUtils.getRootPath(blobPath), commit);
		for (final PathModel path : paths) {
			if (path.path.equals(blobPath)) {
				pathModel = path;
				break;
			}
		}

		if (pathModel == null) {
			final String notFound = MessageFormat.format(
					"Blame page failed to find {0} in {1} @ {2}", blobPath, this.repositoryName,
					this.objectId);
			this.logger.error(notFound);
			add(new Label("annotation").setVisible(false));
			add(new Label("missingBlob", missingBlob(blobPath, commit))
					.setEscapeModelStrings(false));
			return;
		}

		if (pathModel.isFilestoreItem()) {
			final String rawUrl = JGitUtils.getLfsRepositoryUrl(getContextUrl(),
					this.repositoryName, pathModel.getFilestoreOid());
			add(new ExternalLink("blobLink", rawUrl));
		} else {
			add(new BookmarkablePageLink<Void>("blobLink", BlobPage.class,
					WicketUtils.newPathParameter(this.repositoryName, this.objectId, blobPath)));
		}

		add(new BookmarkablePageLink<Void>("commitLink", CommitPage.class,
				WicketUtils.newObjectParameter(this.repositoryName, this.objectId)));
		add(new BookmarkablePageLink<Void>("commitDiffLink", CommitDiffPage.class,
				WicketUtils.newObjectParameter(this.repositoryName, this.objectId)));

		// blame page links
		add(new BookmarkablePageLink<Void>("historyLink", HistoryPage.class,
				WicketUtils.newPathParameter(this.repositoryName, this.objectId, blobPath)));

		// "Blame by" links
		for (final BlameType type : BlameType.values()) {
			final String typeString = type.toString();
			final PageParameters blameTypePageParam = WicketUtils.newBlameTypeParameter(
					this.repositoryName, commit.getName(), WicketUtils.getPath(params), typeString);

			final String blameByLinkText = "blameBy" + Character.toUpperCase(typeString.charAt(0))
					+ typeString.substring(1) + "Link";
			final BookmarkablePageLink<Void> blameByPageLink = new BookmarkablePageLink<Void>(
					blameByLinkText, BlamePage.class, blameTypePageParam);

			if (activeBlameType == type) {
				blameByPageLink.add(new SimpleAttributeModifier("style", "font-weight:bold;"));
			}

			add(blameByPageLink);
		}

		add(new CommitHeaderPanel("commitHeader", this.repositoryName, commit));

		add(new PathBreadcrumbsPanel("breadcrumbs", this.repositoryName, blobPath, this.objectId));

		final String format = app().settings().getString(Keys.web.datetimestampLongFormat,
				"EEEE, MMMM d, yyyy HH:mm Z");
		final DateFormat df = new SimpleDateFormat(format);
		df.setTimeZone(getTimeZone());

		add(new Label("missingBlob").setVisible(false));

		final int tabLength = app().settings().getInteger(Keys.web.tabLength, 4);
		final List<AnnotatedLine> lines = DiffUtils.blame(getRepository(), blobPath, this.objectId);
		final Map<?, String> colorMap = initializeColors(activeBlameType, lines);
		final ListDataProvider<AnnotatedLine> blameDp = new ListDataProvider<AnnotatedLine>(lines);
		final DataView<AnnotatedLine> blameView = new DataView<AnnotatedLine>("annotation", blameDp) {
			private static final long serialVersionUID = 1L;
			private String lastCommitId = "";
			private boolean showInitials = true;
			private final String zeroId = ObjectId.zeroId().getName();

			@Override
			public void populateItem(final Item<AnnotatedLine> item) {
				final AnnotatedLine entry = item.getModelObject();

				// commit id and author
				if (!this.lastCommitId.equals(entry.commitId)) {
					this.lastCommitId = entry.commitId;
					if (this.zeroId.equals(entry.commitId)) {
						// unknown commit
						item.add(new Label("commit", "<?>"));
						this.showInitials = false;
					} else {
						// show the link for first line
						final LinkPanel commitLink = new LinkPanel("commit", null,
								getShortObjectId(entry.commitId), CommitPage.class,
								newCommitParameter(entry.commitId));
						WicketUtils.setHtmlTooltip(
								commitLink,
								MessageFormat.format("{0}, {1}", entry.author,
										df.format(entry.when)));
						item.add(commitLink);
						WicketUtils.setCssStyle(item, "border-top: 1px solid #ddd;");
						this.showInitials = true;
					}
				} else {
					if (this.showInitials) {
						this.showInitials = false;
						// show author initials
						item.add(new Label("commit", getInitials(entry.author)));
					} else {
						// hide the commit link until the next block
						item.add(new Label("commit").setVisible(false));
					}
				}

				// line number
				item.add(new Label("line", "" + entry.lineNumber));

				// line content
				String color;
				switch (activeBlameType) {
				case AGE:
					color = colorMap.get(entry.when);
					break;
				case AUTHOR:
					color = colorMap.get(entry.author);
					break;
				default:
					color = colorMap.get(entry.commitId);
					break;
				}
				final Component data = new Label("data", StringUtils.escapeForHtml(entry.data,
						true, tabLength)).setEscapeModelStrings(false);
				data.add(new SimpleAttributeModifier("style", "background-color: " + color + ";"));
				item.add(data);
			}
		};
		add(blameView);
	}

	private static String getInitials(String author) {
		final StringBuilder sb = new StringBuilder();
		final String[] chunks = author.split(" ");
		for (final String chunk : chunks) {
			sb.append(chunk.charAt(0));
		}
		return sb.toString().toUpperCase();
	}

	@Override
	protected String getPageName() {
		return getString("gb.blame");
	}

	@Override
	protected boolean isCommitPage() {
		return true;
	}

	@Override
	protected Class<? extends BasePage> getRepoNavPageClass() {
		return TreePage.class;
	}

	protected String missingBlob(String blobPath, RevCommit commit) {
		final StringBuilder sb = new StringBuilder();
		sb.append("<div class=\"alert alert-error\">");
		final String pattern = getString("gb.doesNotExistInTree").replace("{0}", "<b>{0}</b>")
				.replace("{1}", "<b>{1}</b>");
		sb.append(MessageFormat.format(pattern, blobPath, commit.getTree().getId().getName()));
		sb.append("</div>");
		return sb.toString();
	}

	private static Map<?, String> initializeColors(BlameType blameType, List<AnnotatedLine> lines) {
		final ColorFactory colorFactory = new ColorFactory();
		Map<?, String> colorMap;

		if (BlameType.AGE == blameType) {
			final Set<Date> keys = new TreeSet<Date>(new Comparator<Date>() {
				@Override
				public int compare(Date o1, Date o2) {
					// younger code has a brighter, older code lightens to white
					return o1.compareTo(o2);
				}
			});

			for (final AnnotatedLine line : lines) {
				keys.add(line.when);
			}

			// TODO consider making this a setting
			colorMap = colorFactory.getGraduatedColorMap(keys, Color.decode("#FFA63A"));
		} else {
			final Set<String> keys = new HashSet<String>();

			for (final AnnotatedLine line : lines) {
				if (blameType == BlameType.AUTHOR) {
					keys.add(line.author);
				} else {
					keys.add(line.commitId);
				}
			}

			colorMap = colorFactory.getRandomColorMap(keys);
		}

		return colorMap;
	}
}
