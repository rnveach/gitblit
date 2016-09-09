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
package com.gitblit.servlet;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Date;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IFilestoreManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.utils.CompressionUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.MarkdownUtils;
import com.gitblit.utils.StringUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Streams out a zip file from the specified repository for any tree path at any
 * revision.
 *
 * @author James Moger
 *
 */
@Singleton
public class DownloadZipServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private transient Logger logger = LoggerFactory.getLogger(DownloadZipServlet.class);

	private final IStoredSettings settings;

	private final IRepositoryManager repositoryManager;

	private final IFilestoreManager filestoreManager;

	public static enum Format {
		zip(".zip"),
		tar(".tar"),
		gz(".tar.gz"),
		xz(".tar.xz"),
		bzip2(".tar.bzip2");

		public final String extension;

		Format(String ext) {
			this.extension = ext;
		}

		public static Format fromName(String name) {
			for (final Format format : values()) {
				if (format.name().equalsIgnoreCase(name)) {
					return format;
				}
			}
			return zip;
		}
	}

	@Inject
	public DownloadZipServlet(IStoredSettings settings, IRepositoryManager repositoryManager,
			IFilestoreManager filestoreManager) {
		this.settings = settings;
		this.repositoryManager = repositoryManager;
		this.filestoreManager = filestoreManager;
	}

	/**
	 * Returns an url to this servlet for the specified parameters.
	 *
	 * @param baseURL
	 * @param repository
	 * @param objectId
	 * @param path
	 * @param format
	 * @return an url
	 */
	public static String asLink(String baseURL, String repository, String objectId, String path,
			Format format) {
		if ((baseURL.length() > 0) && (baseURL.charAt(baseURL.length() - 1) == '/')) {
			baseURL = baseURL.substring(0, baseURL.length() - 1);
		}
		return baseURL + Constants.ZIP_PATH + "?r=" + repository
				+ (path == null ? "" : ("&p=" + path))
				+ (objectId == null ? "" : ("&h=" + objectId))
				+ (format == null ? "" : ("&format=" + format.name()));
	}

	/**
	 * Creates a zip stream from the repository of the requested data.
	 *
	 * @param request
	 * @param response
	 * @throws javax.servlet.ServletException
	 * @throws java.io.IOException
	 */
	private void processRequest(javax.servlet.http.HttpServletRequest request,
			javax.servlet.http.HttpServletResponse response) throws java.io.IOException {
		if (!this.settings.getBoolean(Keys.web.allowZipDownloads, true)) {
			this.logger.warn("Zip downloads are disabled");
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		Format format = Format.zip;
		final String repository = request.getParameter("r");
		final String basePath = request.getParameter("p");
		final String objectId = request.getParameter("h");
		final String f = request.getParameter("format");
		if (!StringUtils.isEmpty(f)) {
			format = Format.fromName(f);
		}

		try {
			String name = repository;
			if (name.indexOf('/') > -1) {
				name = name.substring(name.lastIndexOf('/') + 1);
			}
			name = StringUtils.stripDotGit(name);

			if (!StringUtils.isEmpty(basePath)) {
				name += "-" + basePath.replace('/', '_');
			}
			if (!StringUtils.isEmpty(objectId)) {
				name += "-" + objectId;
			}

			final Repository r = this.repositoryManager.getRepository(repository);
			if (r == null) {
				if (this.repositoryManager.isCollectingGarbage(repository)) {
					error(response, MessageFormat.format(
							"# Error\nGitblit is busy collecting garbage in {0}", repository));
					return;
				} else {
					error(response, MessageFormat.format("# Error\nFailed to find repository {0}",
							repository));
					return;
				}
			}
			final RevCommit commit = JGitUtils.getCommit(r, objectId);
			if (commit == null) {
				error(response,
						MessageFormat.format("# Error\nFailed to find commit {0}", objectId));
				r.close();
				return;
			}
			final Date date = JGitUtils.getCommitDate(commit);

			final String contentType = "application/octet-stream";
			response.setContentType(contentType + "; charset=" + response.getCharacterEncoding());
			response.setHeader("Content-Disposition", "attachment; filename=\"" + name
					+ format.extension + "\"");
			response.setDateHeader("Last-Modified", date.getTime());
			response.setHeader("Cache-Control", "no-cache");
			response.setHeader("Pragma", "no-cache");
			response.setDateHeader("Expires", 0);

			try {
				switch (format) {
				case zip:
					CompressionUtils.zip(r, this.filestoreManager, basePath, objectId,
							response.getOutputStream());
					break;
				case tar:
					CompressionUtils.tar(r, this.filestoreManager, basePath, objectId,
							response.getOutputStream());
					break;
				case gz:
					CompressionUtils.gz(r, this.filestoreManager, basePath, objectId,
							response.getOutputStream());
					break;
				case xz:
					CompressionUtils.xz(r, this.filestoreManager, basePath, objectId,
							response.getOutputStream());
					break;
				case bzip2:
					CompressionUtils.bzip2(r, this.filestoreManager, basePath, objectId,
							response.getOutputStream());
					break;
				}

				response.flushBuffer();
			}
			catch (final IOException t) {
				final String message = t.getMessage() == null ? "" : t.getMessage().toLowerCase();
				if (message.contains("reset") || message.contains("broken pipe")) {
					this.logger.error("Client aborted zip download: " + message);
				} else {
					this.logger.error("Failed to write attachment to client", t);
				}
			}
			catch (final Throwable t) {
				this.logger.error("Failed to write attachment to client", t);
			}

			// close the repository
			r.close();
		}
		catch (final Throwable t) {
			this.logger.error("Failed to write attachment to client", t);
		}
	}

	private static void error(HttpServletResponse response, String mkd) throws IOException {
		final String content = MarkdownUtils.transformMarkdown(mkd);
		response.setContentType("text/html; charset=" + Constants.ENCODING);
		response.getWriter().write(content);
	}

	@Override
	protected void doPost(javax.servlet.http.HttpServletRequest request,
			javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException,
			java.io.IOException {
		processRequest(request, response);
	}

	@Override
	protected void doGet(javax.servlet.http.HttpServletRequest request,
			javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException,
			java.io.IOException {
		processRequest(request, response);
	}
}
