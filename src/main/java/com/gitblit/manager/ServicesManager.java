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
package com.gitblit.manager;

import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.FederationToken;
import com.gitblit.Constants.Transport;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.fanout.FanoutNioService;
import com.gitblit.fanout.FanoutService;
import com.gitblit.fanout.FanoutSocketService;
import com.gitblit.models.FederationModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.RepositoryUrl;
import com.gitblit.models.UserModel;
import com.gitblit.service.FederationPullService;
import com.gitblit.transport.git.GitDaemon;
import com.gitblit.transport.ssh.SshDaemon;
import com.gitblit.utils.HttpUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.utils.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/**
 * Services manager manages long-running services/processes that either have no
 * direct relation to other managers OR require really high-level manager
 * integration (i.e. a Gitblit instance).
 *
 * @author James Moger
 *
 */
@Singleton
public class ServicesManager implements IServicesManager {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(5);

	private final Provider<WorkQueue> workQueueProvider;

	private final IStoredSettings settings;

	private final IGitblit gitblit;

	private FanoutService fanoutService;

	private GitDaemon gitDaemon;

	private SshDaemon sshDaemon;

	@Inject
	public ServicesManager(Provider<WorkQueue> workQueueProvider, IStoredSettings settings,
			IGitblit gitblit) {

		this.workQueueProvider = workQueueProvider;

		this.settings = settings;
		this.gitblit = gitblit;
	}

	@Override
	public ServicesManager start() {
		configureFederation();
		configureFanout();
		configureGitDaemon();
		configureSshDaemon();

		return this;
	}

	@Override
	public ServicesManager stop() {
		this.scheduledExecutor.shutdownNow();
		if (this.fanoutService != null) {
			this.fanoutService.stop();
		}
		if (this.gitDaemon != null) {
			this.gitDaemon.stop();
		}
		if (this.sshDaemon != null) {
			this.sshDaemon.stop();
		}
		this.workQueueProvider.get().stop();
		return this;
	}

	protected String getRepositoryUrl(HttpServletRequest request, String username,
			RepositoryModel repository) {
		String gitblitUrl = this.settings.getString(Keys.web.canonicalUrl, null);
		if (StringUtils.isEmpty(gitblitUrl)) {
			gitblitUrl = HttpUtils.getGitblitURL(request);
		}
		final StringBuilder sb = new StringBuilder();
		sb.append(gitblitUrl);
		sb.append(Constants.R_PATH);
		sb.append(repository.name);

		// inject username into repository url if authentication is required
		if (repository.accessRestriction.exceeds(AccessRestrictionType.NONE)
				&& !StringUtils.isEmpty(username)) {
			sb.insert(sb.indexOf("://") + 3, username + "@");
		}
		return sb.toString();
	}

	/**
	 * Returns a list of repository URLs and the user access permission.
	 *
	 * @param request
	 * @param user
	 * @param repository
	 * @return a list of repository urls
	 */
	@Override
	public List<RepositoryUrl> getRepositoryUrls(HttpServletRequest request, UserModel user,
			RepositoryModel repository) {
		if (user == null) {
			user = UserModel.ANONYMOUS;
		}
		final String username = StringUtils.encodeUsername(UserModel.ANONYMOUS.equals(user) ? ""
				: user.username);

		final List<RepositoryUrl> list = new ArrayList<RepositoryUrl>();

		// http/https url
		if (this.settings.getBoolean(Keys.git.enableGitServlet, true)
				&& this.settings.getBoolean(Keys.web.showHttpServletUrls, true)) {
			AccessPermission permission = user.getRepositoryPermission(repository).permission;
			if (permission.exceeds(AccessPermission.NONE)) {
				final String repoUrl = getRepositoryUrl(request, username, repository);
				final Transport transport = Transport.fromUrl(repoUrl);
				if (permission.atLeast(AccessPermission.PUSH) && !acceptsPush(transport)) {
					// downgrade the repo permission for this transport
					// because it is not an acceptable PUSH transport
					permission = AccessPermission.CLONE;
				}
				list.add(new RepositoryUrl(repoUrl, permission));
			}
		}

		// ssh daemon url
		final String sshDaemonUrl = getSshDaemonUrl(request, user, repository);
		if (!StringUtils.isEmpty(sshDaemonUrl)
				&& this.settings.getBoolean(Keys.web.showSshDaemonUrls, true)) {
			AccessPermission permission = user.getRepositoryPermission(repository).permission;
			if (permission.exceeds(AccessPermission.NONE)) {
				if (permission.atLeast(AccessPermission.PUSH) && !acceptsPush(Transport.SSH)) {
					// downgrade the repo permission for this transport
					// because it is not an acceptable PUSH transport
					permission = AccessPermission.CLONE;
				}

				list.add(new RepositoryUrl(sshDaemonUrl, permission));
			}
		}

		// git daemon url
		final String gitDaemonUrl = getGitDaemonUrl(request, user, repository);
		if (!StringUtils.isEmpty(gitDaemonUrl)
				&& this.settings.getBoolean(Keys.web.showGitDaemonUrls, true)) {
			AccessPermission permission = getGitDaemonAccessPermission(user, repository);
			if (permission.exceeds(AccessPermission.NONE)) {
				if (permission.atLeast(AccessPermission.PUSH) && !acceptsPush(Transport.GIT)) {
					// downgrade the repo permission for this transport
					// because it is not an acceptable PUSH transport
					permission = AccessPermission.CLONE;
				}
				list.add(new RepositoryUrl(gitDaemonUrl, permission));
			}
		}

		// add all other urls
		// {0} = repository
		// {1} = username
		final boolean advertisePermsForOther = this.settings.getBoolean(
				Keys.web.advertiseAccessPermissionForOtherUrls, false);
		for (final String url : this.settings.getStrings(Keys.web.otherUrls)) {
			String externalUrl = null;

			if (url.contains("{1}")) {
				// external url requires username, only add url IF we have one
				if (StringUtils.isEmpty(username)) {
					continue;
				} else {
					externalUrl = MessageFormat.format(url, repository.name, username);
				}
			} else {
				// external url does not require username, just do repo name
				// formatting
				externalUrl = MessageFormat.format(url, repository.name);
			}

			AccessPermission permission = null;
			if (advertisePermsForOther) {
				permission = user.getRepositoryPermission(repository).permission;
				if (permission.exceeds(AccessPermission.NONE)) {
					final Transport transport = Transport.fromUrl(externalUrl);
					if (permission.atLeast(AccessPermission.PUSH) && !acceptsPush(transport)) {
						// downgrade the repo permission for this transport
						// because it is not an acceptable PUSH transport
						permission = AccessPermission.CLONE;
					}
				}
			}
			list.add(new RepositoryUrl(externalUrl, permission));
		}

		// sort transports by highest permission and then by transport security
		Collections.sort(list, new Comparator<RepositoryUrl>() {

			@Override
			public int compare(RepositoryUrl o1, RepositoryUrl o2) {
				if (o1.hasPermission() && !o2.hasPermission()) {
					// prefer known permission items over unknown
					return -1;
				} else if (!o1.hasPermission() && o2.hasPermission()) {
					// prefer known permission items over unknown
					return 1;
				} else if (!o1.hasPermission() && !o2.hasPermission()) {
					// sort by Transport ordinal
					return o1.transport.compareTo(o2.transport);
				} else if (o1.permission.exceeds(o2.permission)) {
					// prefer highest permission
					return -1;
				} else if (o2.permission.exceeds(o1.permission)) {
					// prefer highest permission
					return 1;
				}

				// prefer more secure transports
				return o1.transport.compareTo(o2.transport);
			}
		});

		// consider the user's transport preference
		RepositoryUrl preferredUrl = null;
		final Transport preferredTransport = user.getPreferences().getTransport();
		if (preferredTransport != null) {
			final Iterator<RepositoryUrl> itr = list.iterator();
			while (itr.hasNext()) {
				final RepositoryUrl url = itr.next();
				if (url.transport.equals(preferredTransport)) {
					itr.remove();
					preferredUrl = url;
					break;
				}
			}
		}
		if (preferredUrl != null) {
			list.add(0, preferredUrl);
		}

		return list;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.manager.IServicesManager#isServingRepositories()
	 */
	@Override
	public boolean isServingRepositories() {
		return isServingHTTPS() || isServingHTTP() || isServingGIT() || isServingSSH();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.manager.IServicesManager#isServingHTTP()
	 */
	@Override
	public boolean isServingHTTP() {
		return this.settings.getBoolean(Keys.git.enableGitServlet, true)
				&& ((this.gitblit.getStatus().isGO && (this.settings.getInteger(
						Keys.server.httpPort, 0) > 0)) || !this.gitblit.getStatus().isGO);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.manager.IServicesManager#isServingHTTPS()
	 */
	@Override
	public boolean isServingHTTPS() {
		return this.settings.getBoolean(Keys.git.enableGitServlet, true)
				&& ((this.gitblit.getStatus().isGO && (this.settings.getInteger(
						Keys.server.httpsPort, 0) > 0)) || !this.gitblit.getStatus().isGO);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.manager.IServicesManager#isServingGIT()
	 */
	@Override
	public boolean isServingGIT() {
		return (this.gitDaemon != null) && this.gitDaemon.isRunning();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.manager.IServicesManager#isServingSSH()
	 */
	@Override
	public boolean isServingSSH() {
		return (this.sshDaemon != null) && this.sshDaemon.isRunning();
	}

	protected void configureFederation() {
		boolean validPassphrase = true;
		final String passphrase = this.settings.getString(Keys.federation.passphrase, "");
		if (StringUtils.isEmpty(passphrase)) {
			this.logger.info("Federation passphrase is blank! This server can not be PULLED from.");
			validPassphrase = false;
		}
		if (validPassphrase) {
			// standard tokens
			for (final FederationToken tokenType : FederationToken.values()) {
				this.logger.info(MessageFormat.format("Federation {0} token = {1}",
						tokenType.name(), this.gitblit.getFederationToken(tokenType)));
			}

			// federation set tokens
			for (final String set : this.settings.getStrings(Keys.federation.sets)) {
				this.logger.info(MessageFormat.format("Federation Set {0} token = {1}", set,
						this.gitblit.getFederationToken(set)));
			}
		}

		// Schedule or run the federation executor
		final List<FederationModel> registrations = this.gitblit.getFederationRegistrations();
		if (registrations.size() > 0) {
			final FederationPuller executor = new FederationPuller(registrations);
			this.scheduledExecutor.schedule(executor, 1, TimeUnit.MINUTES);
		}
	}

	@Override
	public boolean acceptsPush(Transport byTransport) {
		if (byTransport == null) {
			this.logger.info("Unknown transport, push rejected!");
			return false;
		}

		final Set<Transport> transports = new HashSet<Transport>();
		for (final String value : this.settings.getStrings(Keys.git.acceptedPushTransports)) {
			final Transport transport = Transport.fromString(value);
			if (transport == null) {
				this.logger.info(String.format("Ignoring unknown registered transport %s", value));
				continue;
			}

			transports.add(transport);
		}

		if (transports.isEmpty()) {
			// no transports are explicitly specified, all are acceptable
			return true;
		}

		// verify that the transport is permitted
		return transports.contains(byTransport);
	}

	protected void configureGitDaemon() {
		final int port = this.settings.getInteger(Keys.git.daemonPort, 0);
		final String bindInterface = this.settings.getString(Keys.git.daemonBindInterface,
				"localhost");
		if (port > 0) {
			try {
				this.gitDaemon = new GitDaemon(this.gitblit);
				this.gitDaemon.start();
			}
			catch (final IOException e) {
				this.gitDaemon = null;
				this.logger.error(MessageFormat.format(
						"Failed to start Git Daemon on {0}:{1,number,0}", bindInterface, port), e);
			}
		} else {
			this.logger.info("Git Daemon is disabled.");
		}
	}

	protected void configureSshDaemon() {
		final int port = this.settings.getInteger(Keys.git.sshPort, 0);
		final String bindInterface = this.settings
				.getString(Keys.git.sshBindInterface, "localhost");
		if (port > 0) {
			try {
				this.sshDaemon = new SshDaemon(this.gitblit, this.workQueueProvider.get());
				this.sshDaemon.start();
			}
			catch (final IOException e) {
				this.sshDaemon = null;
				this.logger.error(MessageFormat.format(
						"Failed to start SSH daemon on {0}:{1,number,0}", bindInterface, port), e);
			}
		}
	}

	protected void configureFanout() {
		// startup Fanout PubSub service
		if (this.settings.getInteger(Keys.fanout.port, 0) > 0) {
			final String bindInterface = this.settings.getString(Keys.fanout.bindInterface, null);
			final int port = this.settings.getInteger(Keys.fanout.port, FanoutService.DEFAULT_PORT);
			final boolean useNio = this.settings.getBoolean(Keys.fanout.useNio, true);
			final int limit = this.settings.getInteger(Keys.fanout.connectionLimit, 0);

			if (useNio) {
				if (StringUtils.isEmpty(bindInterface)) {
					this.fanoutService = new FanoutNioService(port);
				} else {
					this.fanoutService = new FanoutNioService(bindInterface, port);
				}
			} else {
				if (StringUtils.isEmpty(bindInterface)) {
					this.fanoutService = new FanoutSocketService(port);
				} else {
					this.fanoutService = new FanoutSocketService(bindInterface, port);
				}
			}

			this.fanoutService.setConcurrentConnectionLimit(limit);
			this.fanoutService.setAllowAllChannelAnnouncements(false);
			this.fanoutService.start();
		} else {
			this.logger.info("Fanout PubSub service is disabled.");
		}
	}

	public String getGitDaemonUrl(HttpServletRequest request, UserModel user,
			RepositoryModel repository) {
		if (this.gitDaemon != null) {
			final String bindInterface = this.settings.getString(Keys.git.daemonBindInterface,
					"localhost");
			if (bindInterface.equals("localhost")
					&& (!request.getServerName().equals("localhost") && !request.getServerName()
							.equals("127.0.0.1"))) {
				// git daemon is bound to localhost and the request is from
				// elsewhere
				return null;
			}
			if (user.canClone(repository)) {
				final String hostname = getHostname(request);
				final String url = this.gitDaemon.formatUrl(hostname, repository.name);
				return url;
			}
		}
		return null;
	}

	public AccessPermission getGitDaemonAccessPermission(UserModel user, RepositoryModel repository) {
		if ((this.gitDaemon != null) && user.canClone(repository)) {
			AccessPermission gitDaemonPermission = user.getRepositoryPermission(repository).permission;
			if (gitDaemonPermission.atLeast(AccessPermission.CLONE)) {
				if (repository.accessRestriction.atLeast(AccessRestrictionType.CLONE)) {
					// can not authenticate clone via anonymous git protocol
					gitDaemonPermission = AccessPermission.NONE;
				} else if (repository.accessRestriction.atLeast(AccessRestrictionType.PUSH)) {
					// can not authenticate push via anonymous git protocol
					gitDaemonPermission = AccessPermission.CLONE;
				} else {
					// normal user permission
				}
			}
			return gitDaemonPermission;
		}
		return AccessPermission.NONE;
	}

	public String getSshDaemonUrl(HttpServletRequest request, UserModel user,
			RepositoryModel repository) {
		if ((user == null) || UserModel.ANONYMOUS.equals(user)) {
			// SSH always requires authentication - anonymous access prohibited
			return null;
		}
		if (this.sshDaemon != null) {
			final String bindInterface = this.settings.getString(Keys.git.sshBindInterface,
					"localhost");
			if (bindInterface.equals("localhost")
					&& (!request.getServerName().equals("localhost") && !request.getServerName()
							.equals("127.0.0.1"))) {
				// ssh daemon is bound to localhost and the request is from
				// elsewhere
				return null;
			}
			if (user.canClone(repository)) {
				final String hostname = getHostname(request);
				final String url = this.sshDaemon.formatUrl(user.username, hostname,
						repository.name);
				return url;
			}
		}
		return null;
	}

	/**
	 * Extract the hostname from the canonical url or return the hostname from
	 * the servlet request.
	 *
	 * @param request
	 * @return
	 */
	protected String getHostname(HttpServletRequest request) {
		String hostname = request.getServerName();
		final String canonicalUrl = this.settings.getString(Keys.web.canonicalUrl, null);
		if (!StringUtils.isEmpty(canonicalUrl)) {
			try {
				final URI uri = new URI(canonicalUrl);
				final String host = uri.getHost();
				if (!StringUtils.isEmpty(host) && !"localhost".equals(host)) {
					hostname = host;
				}
			}
			catch (final Exception e) {
			}
		}
		return hostname;
	}

	private class FederationPuller extends FederationPullService {

		public FederationPuller(FederationModel registration) {
			super(ServicesManager.this.gitblit, Arrays.asList(registration));
		}

		public FederationPuller(List<FederationModel> registrations) {
			super(ServicesManager.this.gitblit, registrations);
		}

		@Override
		public void reschedule(FederationModel registration) {
			// schedule the next pull
			final int mins = TimeUtils.convertFrequencyToMinutes(registration.frequency, 5);
			registration.nextPull = new Date(System.currentTimeMillis() + (mins * 60 * 1000L));
			ServicesManager.this.scheduledExecutor.schedule(new FederationPuller(registration),
					mins, TimeUnit.MINUTES);
			ServicesManager.this.logger.info(MessageFormat.format(
					"Next pull of {0} @ {1} scheduled for {2,date,yyyy-MM-dd HH:mm}",
					registration.name, registration.url, registration.nextPull));
		}
	}
}
