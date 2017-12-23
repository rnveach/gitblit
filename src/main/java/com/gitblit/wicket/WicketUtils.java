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
package com.gitblit.wicket;

import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.Component;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.markup.html.IHeaderContributor;
import org.apache.wicket.markup.html.IHeaderResponse;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.ContextImage;
import org.apache.wicket.model.Model;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;
import org.apache.wicket.request.IRequestParameters;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.ContextRelativeResource;
import org.apache.wicket.util.string.StringValue;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.FederationPullStatus;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.models.FederationModel;
import com.gitblit.models.Metric;
import com.gitblit.utils.DiffUtils.DiffComparator;
import com.gitblit.utils.HttpUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;

public class WicketUtils {

	public static void setCssClass(Component container, String value) {
		container.add(new SimpleAttributeModifier("class", value));
	}

	public static void addCssClass(Component container, String value) {
		container.add(new AttributeAppender("class", new Model<String>(value), " "));
	}

	public static void setCssStyle(Component container, String value) {
		container.add(new SimpleAttributeModifier("style", value));
	}

	public static void setCssBackground(Component container, String value) {
		String background = MessageFormat.format("background-color:{0};",
				StringUtils.getColor(value));
		container.add(new SimpleAttributeModifier("style", background));
	}

	public static Component setHtmlTooltip(Component container, String value) {
		return container.add(new SimpleAttributeModifier("title", value));
	}

	public static void setInputPlaceholder(Component container, String value) {
		container.add(new SimpleAttributeModifier("placeholder", value));
	}

	public static void setChangeTypeCssClass(Component container, ChangeType type) {
		switch (type) {
		case ADD:
			setCssClass(container, "addition");
			break;
		case COPY:
		case RENAME:
			setCssClass(container, "rename");
			break;
		case DELETE:
			setCssClass(container, "deletion");
			break;
		case MODIFY:
			setCssClass(container, "modification");
			break;
		}
	}

	public static void setPermissionClass(Component container, AccessPermission permission) {
		if (permission == null) {
			setCssClass(container, "badge");
			return;
		}
		switch (permission) {
		case REWIND:
		case DELETE:
		case CREATE:
			setCssClass(container, "badge badge-success");
			break;
		case PUSH:
			setCssClass(container, "badge badge-info");
			break;
		case CLONE:
			setCssClass(container, "badge badge-inverse");
			break;
		default:
			setCssClass(container, "badge");
			break;
		}
	}

	public static void setAlternatingBackground(Component c, int i) {
		String clazz = i % 2 == 0 ? "light" : "dark";
		setCssClass(c, clazz);
	}

	public static Label createAuthorLabel(String wicketId, String author) {
		Label label = new Label(wicketId, author);
		WicketUtils.setHtmlTooltip(label, author);
		return label;
	}

	public static ContextImage getPullStatusImage(String wicketId, FederationPullStatus status) {
		String filename = null;
		switch (status) {
		case MIRRORED:
		case PULLED:
			filename = "bullet_green.png";
			break;
		case SKIPPED:
			filename = "bullet_yellow.png";
			break;
		case FAILED:
			filename = "bullet_red.png";
			break;
		case EXCLUDED:
			filename = "bullet_white.png";
			break;
		case PENDING:
		case NOCHANGE:
		default:
			filename = "bullet_black.png";
		}
		return WicketUtils.newImage(wicketId, filename, status.name());
	}

	public static ContextImage getFileImage(String wicketId, String filename) {
		filename = filename.toLowerCase();
		if (filename.endsWith(".java")) {
			return newImage(wicketId, "file_java_16x16.png");
		} else if (filename.endsWith(".rb")) {
			return newImage(wicketId, "file_ruby_16x16.png");
		} else if (filename.endsWith(".php")) {
			return newImage(wicketId, "file_php_16x16.png");
		} else if (filename.endsWith(".cs")) {
			return newImage(wicketId, "file_cs_16x16.png");
		} else if (filename.endsWith(".cpp")) {
			return newImage(wicketId, "file_cpp_16x16.png");
		} else if (filename.endsWith(".c")) {
			return newImage(wicketId, "file_c_16x16.png");
		} else if (filename.endsWith(".h")) {
			return newImage(wicketId, "file_h_16x16.png");
		} else if (filename.endsWith(".sln")) {
			return newImage(wicketId, "file_vs_16x16.png");
		} else if (filename.endsWith(".csv") || filename.endsWith(".xls")
				|| filename.endsWith(".xlsx")) {
			return newImage(wicketId, "file_excel_16x16.png");
		} else if (filename.endsWith(".doc") || filename.endsWith(".docx")) {
			return newImage(wicketId, "file_doc_16x16.png");
		} else if (filename.endsWith(".ppt") || filename.endsWith(".pptx")) {
			return newImage(wicketId, "file_ppt_16x16.png");
		} else if (filename.endsWith(".zip")) {
			return newImage(wicketId, "file_zip_16x16.png");
		} else if (filename.endsWith(".pdf")) {
			return newImage(wicketId, "file_acrobat_16x16.png");
		} else if (filename.endsWith(".htm") || filename.endsWith(".html")) {
			return newImage(wicketId, "file_world_16x16.png");
		} else if (filename.endsWith(".xml")) {
			return newImage(wicketId, "file_code_16x16.png");
		} else if (filename.endsWith(".properties")) {
			return newImage(wicketId, "file_settings_16x16.png");
		}

		String ext = StringUtils.getFileExtension(filename).toLowerCase();
		IStoredSettings settings = GitBlitWebApp.get().settings();
		if (MarkupProcessor.getMarkupExtensions(settings).contains(ext)) {
			return newImage(wicketId, "file_world_16x16.png");
		}
		return newImage(wicketId, "file_16x16.png");
	}

	public static ContextImage getRegistrationImage(String wicketId, FederationModel registration,
			Component c) {
		if (registration.isResultData()) {
			return WicketUtils.newImage(wicketId, "information_16x16.png",
					c.getString("gb.federationResults"));
		} else {
			return WicketUtils.newImage(wicketId, "arrow_left.png",
					c.getString("gb.federationRegistration"));
		}
	}

	public static ContextImage newClearPixel(String wicketId) {
		return newImage(wicketId, "pixel.png");
	}

	public static ContextImage newBlankImage(String wicketId) {
		return newImage(wicketId, "blank.png");
	}

	public static ContextImage newImage(String wicketId, String file) {
		return newImage(wicketId, file, null);
	}

	public static ContextImage newImage(String wicketId, String file, String tooltip) {
		ContextImage img = new ContextImage(wicketId, file);
		if (!StringUtils.isEmpty(tooltip)) {
			setHtmlTooltip(img, tooltip);
		}
		return img;
	}

	public static Label newIcon(String wicketId, String css) {
		Label lbl = new Label(wicketId);
		setCssClass(lbl, css);
		return lbl;
	}

	public static Label newBlankIcon(String wicketId) {
		Label lbl = new Label(wicketId);
		setCssClass(lbl, "");
		lbl.setRenderBodyOnly(true);
		return lbl;
	}

	public static ContextRelativeResource getResource(String file) {
		return new ContextRelativeResource(file);
	}

	public static String getGitblitURL(Request request) {
		HttpServletRequest req = ((ServletWebRequest) request).getContainerRequest();
		return HttpUtils.getGitblitURL(req);
	}

	public static HeaderContributor syndicationDiscoveryLink(final String feedTitle,
			final String url) {
		return new HeaderContributor(new IHeaderContributor() {
			private static final long serialVersionUID = 1L;

			@Override
			public void renderHead(IHeaderResponse response) {
				String contentType = "application/rss+xml";

				StringBuilder buffer = new StringBuilder();
				buffer.append("<link rel=\"alternate\" ");
				buffer.append("type=\"").append(contentType).append("\" ");
				buffer.append("title=\"").append(feedTitle).append("\" ");
				buffer.append("href=\"").append(url).append("\" />");
				response.renderString(buffer.toString());
			}
		});
	}

	public static PageParameters newTokenParameter(String token) {
		final PageParameters result = new PageParameters();
		result.set("t", token);
		return result;
	}

	public static PageParameters newRegistrationParameter(String url,
			String name) {
		final PageParameters result = new PageParameters();
		result.set("u", url);
		result.set("n", name);
		return result;
	}

	public static PageParameters newUsernameParameter(String username) {
		final PageParameters result = new PageParameters();
		result.set("user", username);
		return result;
	}

	public static PageParameters newTeamnameParameter(String teamname) {
		final PageParameters result = new PageParameters();
		result.set("team", teamname);
		return result;
	}

	public static PageParameters newProjectParameter(String projectName) {
		final PageParameters result = new PageParameters();
		result.set("p", projectName);
		return result;
	}

	public static PageParameters newRepositoryParameter(String repositoryName) {
		final PageParameters result = new PageParameters();
		if (!StringUtils.isEmpty(repositoryName)) {
			result.set("r", repositoryName);
		}
		return result;
	}

	public static PageParameters newObjectParameter(String objectId) {
		final PageParameters result = new PageParameters();
		result.set("h", objectId);
		return result;
	}

	public static PageParameters newObjectParameter(String repositoryName,
			String objectId) {
		if (StringUtils.isEmpty(objectId)) {
			return newRepositoryParameter(repositoryName);
		}
		final PageParameters result = new PageParameters();
		result.set("r", repositoryName);
		result.set("h", objectId);
		return result;
	}

	public static PageParameters newDiffParameter(String repositoryName,
			String objectId, DiffComparator diffComparator) {
		if (StringUtils.isEmpty(objectId)) {
			return newRepositoryParameter(repositoryName);
		}
		final PageParameters result = new PageParameters();
		result.set("r", repositoryName);
		result.set("h", objectId);
		result.set("w", "" + diffComparator.ordinal());
		return result;
	}

	public static PageParameters newDiffParameter(String repositoryName,
			String objectId, DiffComparator diffComparator, String blobPath) {
		if (StringUtils.isEmpty(objectId)) {
			return newRepositoryParameter(repositoryName);
		}
		final PageParameters result = new PageParameters();
		result.set("r", repositoryName);
		result.set("h", objectId);
		result.set("w", "" + diffComparator.ordinal());
		result.set("f", blobPath);
		return result;
	}

	public static PageParameters newRangeParameter(String repositoryName,
			String startRange, String endRange) {
		final PageParameters result = new PageParameters();
		result.set("r", repositoryName);
		result.set("h", startRange + ".." + endRange);
		return result;
	}

	public static PageParameters newPathParameter(String repositoryName,
			String objectId, String path) {
		if (StringUtils.isEmpty(path)) {
			return newObjectParameter(repositoryName, objectId);
		}
		final PageParameters result = new PageParameters();
		if (StringUtils.isEmpty(objectId)) {
			result.set("r", repositoryName);
			result.set("f", path);
			return result;
		}
		result.set("r", repositoryName);
		result.set("h", objectId);
		result.set("f", path);
		return result;
	}

	public static PageParameters newLogPageParameter(String repositoryName,
			String objectId, int pageNumber) {
		if (pageNumber <= 1) {
			return newObjectParameter(repositoryName, objectId);
		}
		final PageParameters result = new PageParameters();
		if (StringUtils.isEmpty(objectId)) {
			result.set("r", repositoryName);
			result.set("pg", String.valueOf(pageNumber));
			return result;
		}
		result.set("r", repositoryName);
		result.set("h", objectId);
		result.set("pg", String.valueOf(pageNumber));
		return result;
	}

	public static PageParameters newHistoryPageParameter(String repositoryName,
			String objectId, String path, int pageNumber) {
		if (pageNumber <= 1) {
			return newObjectParameter(repositoryName, objectId);
		}
		final PageParameters result = new PageParameters();
		if (StringUtils.isEmpty(objectId)) {
			result.set("r", repositoryName);
			result.set("f", path);
			result.set("pg", String.valueOf(pageNumber));
			return result;
		}
		result.set("r", repositoryName);
		result.set("h", objectId);
		result.set("f", path);
		result.set("pg", String.valueOf(pageNumber));
		return result;
	}

	public static PageParameters newFilestorePageParameter(int pageNumber, String filter) {
		final PageParameters result = new PageParameters();
		
		if (pageNumber > 1) {
			result.set("pg", String.valueOf(pageNumber));
		}
		if (filter != null) {
			result.set("s", String.valueOf(filter));
		}
		
		return result;
	}

	public static PageParameters newBlobDiffParameter(String repositoryName,
			String baseCommitId, String commitId, String path) {
		final PageParameters result = new PageParameters();
		if (StringUtils.isEmpty(commitId)) {
			result.set("r", repositoryName);
			result.set("f", path);
			result.set("hb", baseCommitId);
			return result;
		}
		result.set("r", repositoryName);
		result.set("h", commitId);
		result.set("f", path);
		result.set("hb", baseCommitId);
		return result;
	}

	public static PageParameters newSearchParameter(String repositoryName,
			String commitId, String search, Constants.SearchType type) {
		final PageParameters result = new PageParameters();
		if (StringUtils.isEmpty(commitId)) {
			result.set("r", repositoryName);
			result.set("s", search);
			result.set("st", type.name());
			return result;
		}
		result.set("r", repositoryName);
		result.set("h", commitId);
		result.set("s", search);
		result.set("st", type.name());
		return result;
	}

	public static PageParameters newSearchParameter(String repositoryName,
			String commitId, String search, Constants.SearchType type,
			int pageNumber) {
		final PageParameters result = new PageParameters();
		if (StringUtils.isEmpty(commitId)) {
			result.set("r", repositoryName);
			result.set("s", search);
			result.set("st", type.name());
			result.set("pg", String.valueOf(pageNumber));
			return result;
		}
		result.set("r", repositoryName);
		result.set("h", commitId);
		result.set("s", search);
		result.set("st", type.name());
		result.set("pg", String.valueOf(pageNumber));
		return result;
	}

	public static PageParameters newBlameTypeParameter(String repositoryName,
			String commitId, String path, String blameType) {
		final PageParameters result = new PageParameters();
		result.set("r", repositoryName);
		result.set("h", commitId);
		result.set("f", path);
		result.set("blametype", blameType);
		return result;
	}

	public static PageParameters newTicketsParameters(String repositoryName, String... states) {
		PageParameters tParams = newRepositoryParameter(repositoryName);
		if (states != null) {
			for (String state : states) {
				tParams.add("status", state);
			}
		}
		return tParams;
	}

	public static PageParameters newOpenTicketsParameter(String repositoryName) {
		return newTicketsParameters(repositoryName, TicketsUI.openStatii);
	}

	public static String getProjectName(PageParameters params) {
		return params.get("p").toString("");
	}

	public static String getRepositoryName(PageParameters params) {
		return params.get("r").toString("");
	}

	public static String getObject(PageParameters params) {
		return params.get("h").toString(null);
	}

	public static String getPath(PageParameters params) {
		return params.get("f").toString(null);
	}

	public static String getBaseObjectId(PageParameters params) {
		return params.get("hb").toString(null);
	}

	public static String getSearchString(PageParameters params) {
		return params.get("s").toString(null);
	}

	public static String getSearchType(PageParameters params) {
		return params.get("st").toString(null);
	}

	public static DiffComparator getDiffComparator(PageParameters params) {
		int ordinal = params.get("w").toInt(0);
		return DiffComparator.values()[ordinal];
	}

	public static int getPage(PageParameters params) {
		// index from 1
		return params.get("pg").toInt(1);
	}

	public static String getRegEx(PageParameters params) {
		return params.get("x").toString("");
	}

	public static String getSet(PageParameters params) {
		return params.get("set").toString("");
	}

	public static String getTeam(PageParameters params) {
		return params.get("team").toString("");
	}

	public static int getDaysBack(PageParameters params) {
		return params.get("db").toInt(0);
	}

	public static String getUsername(PageParameters params) {
		return params.get("user").toString("");
	}

	public static String getTeamname(PageParameters params) {
		return params.get("team").toString("");
	}

	public static String getToken(PageParameters params) {
		return params.get("t").toString("");
	}

	public static String getUrlParameter(PageParameters params) {
		return params.get("u").toString("");
	}

	public static String getNameParameter(PageParameters params) {
		return params.get("n").toString("");
	}

	public static Label createDateLabel(String wicketId, Date date, TimeZone timeZone, TimeUtils timeUtils) {
		return createDateLabel(wicketId, date, timeZone, timeUtils, true);
	}

	public static Label createDateLabel(String wicketId, Date date, TimeZone timeZone, TimeUtils timeUtils, boolean setCss) {
		String format = GitBlitWebApp.get().settings().getString(Keys.web.datestampShortFormat, "MM/dd/yy");
		DateFormat df = new SimpleDateFormat(format);
		if (timeZone == null) {
			timeZone = GitBlitWebApp.get().getTimezone();
		}
		df.setTimeZone(timeZone);
		String dateString;
		if (date.getTime() == 0) {
			dateString = "--";
		} else {
			dateString = df.format(date);
		}
		String title = null;
		if (date.getTime() <= System.currentTimeMillis()) {
			// past
			title = timeUtils.timeAgo(date);
		}
		if (title != null && (System.currentTimeMillis() - date.getTime()) < 10 * 24 * 60 * 60 * 1000L) {
			String tmp = dateString;
			dateString = title;
			title = tmp;
		}
		Label label = new Label(wicketId, dateString);
		if (setCss) {
			WicketUtils.setCssClass(label, timeUtils.timeAgoCss(date));
		}
		if (!StringUtils.isEmpty(title)) {
			WicketUtils.setHtmlTooltip(label, title);
		}
		return label;
	}

	public static Label createTimeLabel(String wicketId, Date date, TimeZone timeZone, TimeUtils timeUtils) {
		String format = GitBlitWebApp.get().settings().getString(Keys.web.timeFormat, "HH:mm");
		DateFormat df = new SimpleDateFormat(format);
		if (timeZone == null) {
			timeZone = GitBlitWebApp.get().getTimezone();
		}
		df.setTimeZone(timeZone);
		String timeString;
		if (date.getTime() == 0) {
			timeString = "--";
		} else {
			timeString = df.format(date);
		}
		String title = timeUtils.timeAgo(date);
		Label label = new Label(wicketId, timeString);
		if (!StringUtils.isEmpty(title)) {
			WicketUtils.setHtmlTooltip(label, title);
		}
		return label;
	}

	public static Label createDatestampLabel(String wicketId, Date date, TimeZone timeZone, TimeUtils timeUtils) {
		String format = GitBlitWebApp.get().settings().getString(Keys.web.datestampLongFormat, "EEEE, MMMM d, yyyy");
		DateFormat df = new SimpleDateFormat(format);
		if (timeZone == null) {
			timeZone = GitBlitWebApp.get().getTimezone();
		}
		df.setTimeZone(timeZone);
		String dateString;
		if (date.getTime() == 0) {
			dateString = "--";
		} else {
			dateString = df.format(date);
		}
		String title = null;
		if (TimeUtils.isToday(date, timeZone)) {
			title = timeUtils.today();
		} else if (TimeUtils.isYesterday(date, timeZone)) {
			title = timeUtils.yesterday();
		} else if (date.getTime() <= System.currentTimeMillis()) {
			// past
			title = timeUtils.timeAgo(date);
		} else {
			// future
			title = timeUtils.inFuture(date);
		}
		if ((System.currentTimeMillis() - date.getTime()) < 10 * 24 * 60 * 60 * 1000L) {
			String tmp = dateString;
			dateString = title;
			title = tmp;
		}
		Label label = new Label(wicketId, dateString);
		if (!StringUtils.isEmpty(title)) {
			WicketUtils.setHtmlTooltip(label, title);
		}
		return label;
	}

	public static Label createTimestampLabel(String wicketId, Date date, TimeZone timeZone, TimeUtils timeUtils) {
		String format = GitBlitWebApp.get().settings().getString(Keys.web.datetimestampLongFormat,
				"EEEE, MMMM d, yyyy HH:mm Z");
		DateFormat df = new SimpleDateFormat(format);
		if (timeZone == null) {
			timeZone = GitBlitWebApp.get().getTimezone();
		}
		df.setTimeZone(timeZone);
		String dateString;
		if (date.getTime() == 0) {
			dateString = "--";
		} else {
			dateString = df.format(date);
		}
		String title = null;
		if (date.getTime() <= System.currentTimeMillis()) {
			// past
			title = timeUtils.timeAgo(date);
		}
		Label label = new Label(wicketId, dateString);
		if (!StringUtils.isEmpty(title)) {
			WicketUtils.setHtmlTooltip(label, title);
		}
		return label;
	}

	public static double maxValue(Collection<Metric> metrics) {
		double max = Double.MIN_VALUE;
		for (Metric m : metrics) {
			if (m.count > max) {
				max = m.count;
			}
		}
		return max;
	}

	public static PageParameters getPageParameters(IRequestParameters params) {
		final PageParameters result = new PageParameters();
		
		for (String name : params.getParameterNames()) {
			result.set(name, params.getParameterValue(name).toString());
		}
		
		return result;
	}
}
