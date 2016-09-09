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
package com.gitblit.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import com.gitblit.Constants;
import com.gitblit.Constants.FeedObjectType;
import com.gitblit.GitBlitException;
import com.gitblit.models.FeedEntryModel;
import com.sun.syndication.feed.synd.SyndCategory;
import com.sun.syndication.feed.synd.SyndCategoryImpl;
import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndContentImpl;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndEntryImpl;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.feed.synd.SyndFeedImpl;
import com.sun.syndication.feed.synd.SyndImageImpl;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.SyndFeedOutput;
import com.sun.syndication.io.XmlReader;

/**
 * Utility class for RSS feeds.
 *
 * @author James Moger
 *
 */
public class SyndicationUtils {

	/**
	 * Outputs an RSS feed of the list of entries to the outputstream.
	 *
	 * @param hostUrl
	 * @param feedLink
	 * @param title
	 * @param description
	 * @param entryModels
	 * @param os
	 * @throws IOException
	 * @throws FeedException
	 */
	public static void toRSS(String hostUrl, String feedLink, String title, String description,
			List<FeedEntryModel> entryModels, OutputStream os) throws IOException, FeedException {

		final SyndFeed feed = new SyndFeedImpl();
		feed.setFeedType("rss_2.0");
		feed.setEncoding("UTF-8");
		feed.setTitle(title);
		feed.setLink(feedLink);
		if (StringUtils.isEmpty(description)) {
			feed.setDescription(title);
		} else {
			feed.setDescription(description);
		}
		final SyndImageImpl image = new SyndImageImpl();
		image.setTitle(Constants.NAME);
		image.setUrl(hostUrl + "/gitblt_25.png");
		image.setLink(hostUrl);
		feed.setImage(image);

		final List<SyndEntry> entries = new ArrayList<SyndEntry>();
		for (final FeedEntryModel entryModel : entryModels) {
			final SyndEntry entry = new SyndEntryImpl();
			entry.setTitle(entryModel.title);
			entry.setAuthor(entryModel.author);
			entry.setLink(entryModel.link);
			entry.setPublishedDate(entryModel.published);

			if ((entryModel.tags != null) && (entryModel.tags.size() > 0)) {
				final List<SyndCategory> tags = new ArrayList<SyndCategory>();
				for (final String tag : entryModel.tags) {
					final SyndCategoryImpl cat = new SyndCategoryImpl();
					cat.setName(tag);
					tags.add(cat);
				}
				entry.setCategories(tags);
			}

			final SyndContent content = new SyndContentImpl();
			if (StringUtils.isEmpty(entryModel.contentType)
					|| entryModel.contentType.equalsIgnoreCase("text/plain")) {
				content.setType("text/html");
				content.setValue(StringUtils.breakLinesForHtml(entryModel.content));
			} else {
				content.setType(entryModel.contentType);
				content.setValue(entryModel.content);
			}
			entry.setDescription(content);

			entries.add(entry);
		}
		feed.setEntries(entries);

		final OutputStreamWriter writer = new OutputStreamWriter(os, "UTF-8");
		final SyndFeedOutput output = new SyndFeedOutput();
		output.output(feed, writer);
		writer.close();
	}

	/**
	 * Reads a Gitblit RSS feed.
	 *
	 * @param url
	 *            the url of the Gitblit server
	 * @param repository
	 *            the repository name
	 * @param branch
	 *            the branch name (optional)
	 * @param numberOfEntries
	 *            the number of entries to retrieve. if <= 0 the server default
	 *            is used.
	 * @param page
	 *            0-indexed. used to paginate the results.
	 * @param username
	 * @param password
	 * @return a list of SyndicationModel entries
	 * @throws {@link IOException}
	 */
	public static List<FeedEntryModel> readFeed(String url, String repository, String branch,
			int numberOfEntries, int page, String username, char[] password) throws IOException {
		return readFeed(url, repository, branch, FeedObjectType.COMMIT, numberOfEntries, page,
				username, password);
	}

	/**
	 * Reads tags from the specified repository.
	 *
	 * @param url
	 *            the url of the Gitblit server
	 * @param repository
	 *            the repository name
	 * @param branch
	 *            the branch name (optional)
	 * @param numberOfEntries
	 *            the number of entries to retrieve. if <= 0 the server default
	 *            is used.
	 * @param page
	 *            0-indexed. used to paginate the results.
	 * @param username
	 * @param password
	 * @return a list of SyndicationModel entries
	 * @throws {@link IOException}
	 */
	public static List<FeedEntryModel> readTags(String url, String repository, int numberOfEntries,
			int page, String username, char[] password) throws IOException {
		return readFeed(url, repository, null, FeedObjectType.TAG, numberOfEntries, page, username,
				password);
	}

	/**
	 * Reads a Gitblit RSS feed.
	 *
	 * @param url
	 *            the url of the Gitblit server
	 * @param repository
	 *            the repository name
	 * @param branch
	 *            the branch name (optional)
	 * @param objectType
	 *            the object type to return (optional, COMMIT assummed)
	 * @param numberOfEntries
	 *            the number of entries to retrieve. if <= 0 the server default
	 *            is used.
	 * @param page
	 *            0-indexed. used to paginate the results.
	 * @param username
	 * @param password
	 * @return a list of SyndicationModel entries
	 * @throws {@link IOException}
	 */
	private static List<FeedEntryModel> readFeed(String url, String repository, String branch,
			FeedObjectType objectType, int numberOfEntries, int page, String username,
			char[] password) throws IOException {
		// build feed url
		final List<String> parameters = new ArrayList<String>();
		if (numberOfEntries > 0) {
			parameters.add("l=" + numberOfEntries);
		}
		if (page > 0) {
			parameters.add("pg=" + page);
		}
		if (!StringUtils.isEmpty(branch)) {
			parameters.add("h=" + branch);
		}
		if (objectType != null) {
			parameters.add("ot=" + objectType.name());
		}
		return readFeed(url, parameters, repository, branch, username, password);
	}

	/**
	 * Reads a Gitblit RSS search feed.
	 *
	 * @param url
	 *            the url of the Gitblit server
	 * @param repository
	 *            the repository name
	 * @param fragment
	 *            the search fragment
	 * @param searchType
	 *            the search type (optional, defaults to COMMIT)
	 * @param numberOfEntries
	 *            the number of entries to retrieve. if <= 0 the server default
	 *            is used.
	 * @param page
	 *            0-indexed. used to paginate the results.
	 * @param username
	 * @param password
	 * @return a list of SyndicationModel entries
	 * @throws {@link IOException}
	 */
	public static List<FeedEntryModel> readSearchFeed(String url, String repository, String branch,
			String fragment, Constants.SearchType searchType, int numberOfEntries, int page,
			String username, char[] password) throws IOException {
		// determine parameters
		final List<String> parameters = new ArrayList<String>();
		parameters.add("s=" + StringUtils.encodeURL(fragment));
		if (numberOfEntries > 0) {
			parameters.add("l=" + numberOfEntries);
		}
		if (page > 0) {
			parameters.add("pg=" + page);
		}
		if (!StringUtils.isEmpty(branch)) {
			parameters.add("h=" + branch);
		}
		if (searchType != null) {
			parameters.add("st=" + searchType.name());
		}
		return readFeed(url, parameters, repository, branch, username, password);
	}

	/**
	 * Reads a Gitblit RSS feed.
	 *
	 * @param url
	 *            the url of the Gitblit server
	 * @param parameters
	 *            the list of RSS parameters
	 * @param repository
	 *            the repository name
	 * @param username
	 * @param password
	 * @return a list of SyndicationModel entries
	 * @throws {@link IOException}
	 */
	private static List<FeedEntryModel> readFeed(String url, List<String> parameters,
			String repository, String branch, String username, char[] password) throws IOException {
		// build url
		final StringBuilder sb = new StringBuilder();
		sb.append(MessageFormat.format("{0}" + Constants.SYNDICATION_PATH + "{1}", url, repository));
		if (parameters.size() > 0) {
			boolean first = true;
			for (final String parameter : parameters) {
				if (first) {
					sb.append('?');
					first = false;
				} else {
					sb.append('&');
				}
				sb.append(parameter);
			}
		}
		final String feedUrl = sb.toString();
		final URLConnection conn = ConnectionUtils.openReadConnection(feedUrl, username, password);
		final InputStream is = conn.getInputStream();
		final SyndFeedInput input = new SyndFeedInput();
		SyndFeed feed = null;
		try {
			feed = input.build(new XmlReader(is));
		}
		catch (final FeedException f) {
			throw new GitBlitException(f);
		}
		is.close();
		final List<FeedEntryModel> entries = new ArrayList<FeedEntryModel>();
		for (final Object o : feed.getEntries()) {
			final SyndEntryImpl entry = (SyndEntryImpl) o;
			final FeedEntryModel model = new FeedEntryModel();
			model.repository = repository;
			model.branch = branch;
			model.title = entry.getTitle();
			model.author = entry.getAuthor();
			model.published = entry.getPublishedDate();
			model.link = entry.getLink();
			model.content = entry.getDescription().getValue();
			model.contentType = entry.getDescription().getType();
			if ((entry.getCategories() != null) && (entry.getCategories().size() > 0)) {
				final List<String> tags = new ArrayList<String>();
				for (final Object p : entry.getCategories()) {
					final SyndCategory cat = (SyndCategory) p;
					tags.add(cat.getName());
				}
				model.tags = tags;
			}
			entries.add(model);
		}
		return entries;
	}
}
