/*
 * Copyright 2012 John Crygier
 * Copyright 2012 gitblit.com
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
package com.gitblit.auth;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.gitblit.Constants;
import com.gitblit.Constants.AccountType;
import com.gitblit.Constants.Role;
import com.gitblit.Keys;
import com.gitblit.auth.AuthenticationProvider.UsernamePasswordAuthenticationProvider;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.service.LdapSyncService;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.DereferencePolicy;
import com.unboundid.ldap.sdk.ExtendedResult;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.SimpleBindRequest;
import com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;

/**
 * Implementation of an LDAP user service.
 *
 * @author John Crygier
 */
public class LdapAuthProvider extends UsernamePasswordAuthenticationProvider {

	private final ScheduledExecutorService scheduledExecutorService;

	public LdapAuthProvider() {
		super("ldap");

		this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
	}

	private long getSynchronizationPeriodInMilliseconds() {
		String period = this.settings.getString(Keys.realm.ldap.syncPeriod, null);
		if (StringUtils.isEmpty(period)) {
			period = this.settings.getString("realm.ldap.ldapCachePeriod", null);
			if (StringUtils.isEmpty(period)) {
				period = "5 MINUTES";
			} else {
				this.logger.warn("realm.ldap.ldapCachePeriod is obsolete!");
				this.logger.warn(MessageFormat.format("Please set {0}={1} in gitblit.properties!",
						Keys.realm.ldap.syncPeriod, period));
				this.settings.overrideSetting(Keys.realm.ldap.syncPeriod, period);
			}
		}

		try {
			final String[] s = period.split(" ", 2);
			final long duration = Math.abs(Long.parseLong(s[0]));
			final TimeUnit timeUnit = TimeUnit.valueOf(s[1]);
			return timeUnit.toMillis(duration);
		}
		catch (final RuntimeException ex) {
			throw new IllegalArgumentException(
					Keys.realm.ldap.syncPeriod
							+ " must have format '<long> <TimeUnit>' where <TimeUnit> is one of 'MILLISECONDS', 'SECONDS', 'MINUTES', 'HOURS', 'DAYS'");
		}
	}

	@Override
	public void setup() {
		configureSyncService();
	}

	@Override
	public void stop() {
		this.scheduledExecutorService.shutdownNow();
	}

	public synchronized void sync() {
		final boolean enabled = this.settings.getBoolean(Keys.realm.ldap.synchronize, false);
		if (enabled) {
			this.logger.info("Synchronizing with LDAP @ "
					+ this.settings.getRequiredString(Keys.realm.ldap.server));
			final boolean deleteRemovedLdapUsers = this.settings.getBoolean(
					Keys.realm.ldap.removeDeletedUsers, true);
			final LDAPConnection ldapConnection = getLdapConnection();
			if (ldapConnection != null) {
				try {
					final String accountBase = this.settings.getString(Keys.realm.ldap.accountBase,
							"");
					final String uidAttribute = this.settings.getString(Keys.realm.ldap.uid, "uid");
					String accountPattern = this.settings.getString(Keys.realm.ldap.accountPattern,
							"(&(objectClass=person)(sAMAccountName=${username}))");
					accountPattern = StringUtils.replace(accountPattern, "${username}", "*");

					final SearchResult result = doSearch(ldapConnection, accountBase,
							accountPattern);
					if ((result != null) && (result.getEntryCount() > 0)) {
						final Map<String, UserModel> ldapUsers = new HashMap<String, UserModel>();

						for (final SearchResultEntry loggingInUser : result.getSearchEntries()) {
							final Attribute uid = loggingInUser.getAttribute(uidAttribute);
							if (uid == null) {
								this.logger.error(
										"Can not synchronize with LDAP, missing \"{}\" attribute",
										uidAttribute);
								continue;
							}
							final String username = uid.getValue();
							this.logger.debug("LDAP synchronizing: " + username);

							UserModel user = this.userManager.getUserModel(username);
							if (user == null) {
								user = new UserModel(username);
							}

							if (!supportsTeamMembershipChanges()) {
								getTeamsFromLdap(ldapConnection, username, loggingInUser, user);
							}

							// Get User Attributes
							setUserAttributes(user, loggingInUser);

							// store in map
							ldapUsers.put(username.toLowerCase(), user);
						}

						if (deleteRemovedLdapUsers) {
							this.logger.debug("detecting removed LDAP users...");

							for (final UserModel userModel : this.userManager.getAllUsers()) {
								if (AccountType.LDAP == userModel.accountType) {
									if (!ldapUsers.containsKey(userModel.username)) {
										this.logger.info("deleting removed LDAP user "
												+ userModel.username + " from user service");
										this.userManager.deleteUser(userModel.username);
									}
								}
							}
						}

						this.userManager.updateUserModels(ldapUsers.values());

						if (!supportsTeamMembershipChanges()) {
							final Map<String, TeamModel> userTeams = new HashMap<String, TeamModel>();
							for (final UserModel user : ldapUsers.values()) {
								for (final TeamModel userTeam : user.teams) {
									userTeams.put(userTeam.name, userTeam);
								}
							}
							this.userManager.updateTeamModels(userTeams.values());
						}
					}
					if (!supportsTeamMembershipChanges()) {
						getEmptyTeamsFromLdap(ldapConnection);
					}
				}
				finally {
					ldapConnection.close();
				}
			}
		}
	}

	private LDAPConnection getLdapConnection() {
		try {

			final URI ldapUrl = new URI(this.settings.getRequiredString(Keys.realm.ldap.server));
			final String ldapHost = ldapUrl.getHost();
			int ldapPort = ldapUrl.getPort();
			final String bindUserName = this.settings.getString(Keys.realm.ldap.username, "");
			final String bindPassword = this.settings.getString(Keys.realm.ldap.password, "");

			LDAPConnection conn;
			if (ldapUrl.getScheme().equalsIgnoreCase("ldaps")) {
				// SSL
				final SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager());
				conn = new LDAPConnection(sslUtil.createSSLSocketFactory());
				if (ldapPort == -1) {
					ldapPort = 636;
				}
			} else if (ldapUrl.getScheme().equalsIgnoreCase("ldap")
					|| ldapUrl.getScheme().equalsIgnoreCase("ldap+tls")) {
				// no encryption or StartTLS
				conn = new LDAPConnection();
				if (ldapPort == -1) {
					ldapPort = 389;
				}
			} else {
				this.logger.error("Unsupported LDAP URL scheme: " + ldapUrl.getScheme());
				return null;
			}

			conn.connect(ldapHost, ldapPort);

			if (ldapUrl.getScheme().equalsIgnoreCase("ldap+tls")) {
				final SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager());
				final ExtendedResult extendedResult = conn
						.processExtendedOperation(new StartTLSExtendedRequest(sslUtil
								.createSSLContext()));
				if (extendedResult.getResultCode() != ResultCode.SUCCESS) {
					throw new LDAPException(extendedResult.getResultCode());
				}
			}

			if (StringUtils.isEmpty(bindUserName) && StringUtils.isEmpty(bindPassword)) {
				// anonymous bind
				conn.bind(new SimpleBindRequest());
			} else {
				// authenticated bind
				conn.bind(new SimpleBindRequest(bindUserName, bindPassword));
			}

			return conn;

		}
		catch (final URISyntaxException e) {
			this.logger.error(
					"Bad LDAP URL, should be in the form: ldap(s|+tls)://<server>:<port>", e);
		}
		catch (final GeneralSecurityException e) {
			this.logger.error("Unable to create SSL Connection", e);
		}
		catch (final LDAPException e) {
			this.logger.error("Error Connecting to LDAP", e);
		}

		return null;
	}

	/**
	 * Credentials are defined in the LDAP server and can not be manipulated
	 * from Gitblit.
	 *
	 * @return false
	 * @since 1.0.0
	 */
	@Override
	public boolean supportsCredentialChanges() {
		return false;
	}

	/**
	 * If no displayName pattern is defined then Gitblit can manage the display
	 * name.
	 *
	 * @return true if Gitblit can manage the user display name
	 * @since 1.0.0
	 */
	@Override
	public boolean supportsDisplayNameChanges() {
		return StringUtils.isEmpty(this.settings.getString(Keys.realm.ldap.displayName, ""));
	}

	/**
	 * If no email pattern is defined then Gitblit can manage the email address.
	 *
	 * @return true if Gitblit can manage the user email address
	 * @since 1.0.0
	 */
	@Override
	public boolean supportsEmailAddressChanges() {
		return StringUtils.isEmpty(this.settings.getString(Keys.realm.ldap.email, ""));
	}

	/**
	 * If the LDAP server will maintain team memberships then LdapUserService
	 * will not allow team membership changes. In this scenario all team changes
	 * must be made on the LDAP server by the LDAP administrator.
	 *
	 * @return true or false
	 * @since 1.0.0
	 */
	@Override
	public boolean supportsTeamMembershipChanges() {
		return !this.settings.getBoolean(Keys.realm.ldap.maintainTeams, false);
	}

	@Override
	public boolean supportsRoleChanges(UserModel user, Role role) {
		if (Role.ADMIN == role) {
			if (!supportsTeamMembershipChanges()) {
				final List<String> admins = this.settings.getStrings(Keys.realm.ldap.admins);
				if (admins.contains(user.username)) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public boolean supportsRoleChanges(TeamModel team, Role role) {
		if (Role.ADMIN == role) {
			if (!supportsTeamMembershipChanges()) {
				final List<String> admins = this.settings.getStrings(Keys.realm.ldap.admins);
				if (admins.contains("@" + team.name)) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public AccountType getAccountType() {
		return AccountType.LDAP;
	}

	@Override
	public UserModel authenticate(String username, char[] password) {
		final String simpleUsername = getSimpleUsername(username);

		final LDAPConnection ldapConnection = getLdapConnection();
		if (ldapConnection != null) {
			try {
				boolean alreadyAuthenticated = false;

				final String bindPattern = this.settings.getString(Keys.realm.ldap.bindpattern, "");
				if (!StringUtils.isEmpty(bindPattern)) {
					try {
						final String bindUser = StringUtils.replace(bindPattern, "${username}",
								escapeLDAPSearchFilter(simpleUsername));
						ldapConnection.bind(bindUser, new String(password));

						alreadyAuthenticated = true;
					}
					catch (final LDAPException e) {
						return null;
					}
				}

				// Find the logging in user's DN
				final String accountBase = this.settings.getString(Keys.realm.ldap.accountBase, "");
				String accountPattern = this.settings.getString(Keys.realm.ldap.accountPattern,
						"(&(objectClass=person)(sAMAccountName=${username}))");
				accountPattern = StringUtils.replace(accountPattern, "${username}",
						escapeLDAPSearchFilter(simpleUsername));

				final SearchResult result = doSearch(ldapConnection, accountBase, accountPattern);
				if ((result != null) && (result.getEntryCount() == 1)) {
					final SearchResultEntry loggingInUser = result.getSearchEntries().get(0);
					final String loggingInUserDN = loggingInUser.getDN();

					if (alreadyAuthenticated
							|| isAuthenticated(ldapConnection, loggingInUserDN,
									new String(password))) {
						this.logger.debug("LDAP authenticated: " + username);

						UserModel user = null;
						synchronized (this) {
							user = this.userManager.getUserModel(simpleUsername);
							if (user == null) {
								// create user object for new authenticated user
								user = new UserModel(simpleUsername);
							}

							// create a user cookie
							setCookie(user, password);

							if (!supportsTeamMembershipChanges()) {
								getTeamsFromLdap(ldapConnection, simpleUsername, loggingInUser,
										user);
							}

							// Get User Attributes
							setUserAttributes(user, loggingInUser);

							// Push the ldap looked up values to backing file
							updateUser(user);

							if (!supportsTeamMembershipChanges()) {
								for (final TeamModel userTeam : user.teams) {
									updateTeam(userTeam);
								}
							}
						}

						return user;
					}
				}
			}
			finally {
				ldapConnection.close();
			}
		}
		return null;
	}

	/**
	 * Set the admin attribute from team memberships retrieved from LDAP. If we
	 * are not storing teams in LDAP and/or we have not defined any
	 * administrator teams, then do not change the admin flag.
	 *
	 * @param user
	 */
	private void setAdminAttribute(UserModel user) {
		if (!supportsTeamMembershipChanges()) {
			final List<String> admins = this.settings.getStrings(Keys.realm.ldap.admins);
			// if we have defined administrative teams, then set admin flag
			// otherwise leave admin flag unchanged
			if (!ArrayUtils.isEmpty(admins)) {
				user.canAdmin = false;
				for (final String admin : admins) {
					if (admin.startsWith("@") && user.isTeamMember(admin.substring(1))) {
						// admin team
						user.canAdmin = true;
					} else if (user.getName().equalsIgnoreCase(admin)) {
						// admin user
						user.canAdmin = true;
					}
				}
			}
		}
	}

	private void setUserAttributes(UserModel user, SearchResultEntry userEntry) {
		// Is this user an admin?
		setAdminAttribute(user);

		// Don't want visibility into the real password, make up a dummy
		user.password = Constants.EXTERNAL_ACCOUNT;
		user.accountType = getAccountType();

		// Get full name Attribute
		String displayName = this.settings.getString(Keys.realm.ldap.displayName, "");
		if (!StringUtils.isEmpty(displayName)) {
			// Replace embedded ${} with attributes
			if (displayName.contains("${")) {
				for (final Attribute userAttribute : userEntry.getAttributes()) {
					displayName = StringUtils.replace(displayName, "${" + userAttribute.getName()
							+ "}", userAttribute.getValue());
				}
				user.displayName = displayName;
			} else {
				final Attribute attribute = userEntry.getAttribute(displayName);
				if ((attribute != null) && attribute.hasValue()) {
					user.displayName = attribute.getValue();
				}
			}
		}

		// Get email address Attribute
		String email = this.settings.getString(Keys.realm.ldap.email, "");
		if (!StringUtils.isEmpty(email)) {
			if (email.contains("${")) {
				for (final Attribute userAttribute : userEntry.getAttributes()) {
					email = StringUtils.replace(email, "${" + userAttribute.getName() + "}",
							userAttribute.getValue());
				}
				user.emailAddress = email;
			} else {
				final Attribute attribute = userEntry.getAttribute(email);
				if ((attribute != null) && attribute.hasValue()) {
					user.emailAddress = attribute.getValue();
				} else {
					// issue-456/ticket-134
					// allow LDAP to delete an email address
					user.emailAddress = null;
				}
			}
		}
	}

	private void getTeamsFromLdap(LDAPConnection ldapConnection, String simpleUsername,
			SearchResultEntry loggingInUser, UserModel user) {
		final String loggingInUserDN = loggingInUser.getDN();

		// Clear the users team memberships - we're going to get them from LDAP
		user.teams.clear();

		final String groupBase = this.settings.getString(Keys.realm.ldap.groupBase, "");
		String groupMemberPattern = this.settings.getString(Keys.realm.ldap.groupMemberPattern,
				"(&(objectClass=group)(member=${dn}))");

		groupMemberPattern = StringUtils.replace(groupMemberPattern, "${dn}",
				escapeLDAPSearchFilter(loggingInUserDN));
		groupMemberPattern = StringUtils.replace(groupMemberPattern, "${username}",
				escapeLDAPSearchFilter(simpleUsername));

		// Fill in attributes into groupMemberPattern
		for (final Attribute userAttribute : loggingInUser.getAttributes()) {
			groupMemberPattern = StringUtils.replace(groupMemberPattern,
					"${" + userAttribute.getName() + "}",
					escapeLDAPSearchFilter(userAttribute.getValue()));
		}

		final SearchResult teamMembershipResult = doSearch(ldapConnection, groupBase, true,
				groupMemberPattern, Arrays.asList("cn"));
		if ((teamMembershipResult != null) && (teamMembershipResult.getEntryCount() > 0)) {
			for (int i = 0; i < teamMembershipResult.getEntryCount(); i++) {
				final SearchResultEntry teamEntry = teamMembershipResult.getSearchEntries().get(i);
				final String teamName = teamEntry.getAttribute("cn").getValue();

				TeamModel teamModel = this.userManager.getTeamModel(teamName);
				if (teamModel == null) {
					teamModel = createTeamFromLdap(teamEntry);
				}

				user.teams.add(teamModel);
				teamModel.addUser(user.getName());
			}
		}
	}

	private void getEmptyTeamsFromLdap(LDAPConnection ldapConnection) {
		this.logger.info("Start fetching empty teams from ldap.");
		final String groupBase = this.settings.getString(Keys.realm.ldap.groupBase, "");
		final String groupMemberPattern = this.settings.getString(
				Keys.realm.ldap.groupEmptyMemberPattern, "(&(objectClass=group)(!(member=*)))");

		final SearchResult teamMembershipResult = doSearch(ldapConnection, groupBase, true,
				groupMemberPattern, null);
		if ((teamMembershipResult != null) && (teamMembershipResult.getEntryCount() > 0)) {
			for (int i = 0; i < teamMembershipResult.getEntryCount(); i++) {
				final SearchResultEntry teamEntry = teamMembershipResult.getSearchEntries().get(i);
				if (!teamEntry.hasAttribute("member")) {
					final String teamName = teamEntry.getAttribute("cn").getValue();

					TeamModel teamModel = this.userManager.getTeamModel(teamName);
					if (teamModel == null) {
						teamModel = createTeamFromLdap(teamEntry);
						this.userManager.updateTeamModel(teamModel);
					}
				}
			}
		}
		this.logger.info("Finished fetching empty teams from ldap.");
	}

	private TeamModel createTeamFromLdap(SearchResultEntry teamEntry) {
		final TeamModel answer = new TeamModel(teamEntry.getAttributeValue("cn"));
		answer.accountType = getAccountType();
		// potentially retrieve other attributes here in the future

		return answer;
	}

	private SearchResult doSearch(LDAPConnection ldapConnection, String base, String filter) {
		try {
			return ldapConnection.search(base, SearchScope.SUB, filter);
		}
		catch (final LDAPSearchException e) {
			this.logger.error("Problem Searching LDAP", e);

			return null;
		}
	}

	private SearchResult doSearch(LDAPConnection ldapConnection, String base,
			boolean dereferenceAliases, String filter, List<String> attributes) {
		try {
			final SearchRequest searchRequest = new SearchRequest(base, SearchScope.SUB, filter);
			if (dereferenceAliases) {
				searchRequest.setDerefPolicy(DereferencePolicy.SEARCHING);
			}
			if (attributes != null) {
				searchRequest.setAttributes(attributes);
			}
			return ldapConnection.search(searchRequest);

		}
		catch (final LDAPSearchException e) {
			this.logger.error("Problem Searching LDAP", e);

			return null;
		}
		catch (final LDAPException e) {
			this.logger.error("Problem creating LDAP search", e);
			return null;
		}
	}

	private boolean isAuthenticated(LDAPConnection ldapConnection, String userDn, String password) {
		try {
			// Binding will stop any LDAP-Injection Attacks since the
			// searched-for user needs to bind to that DN
			ldapConnection.bind(userDn, password);
			return true;
		}
		catch (final LDAPException e) {
			this.logger.error("Error authenticating user", e);
			return false;
		}
	}

	/**
	 * Returns a simple username without any domain prefixes.
	 *
	 * @param username
	 * @return a simple username
	 */
	private static String getSimpleUsername(String username) {
		final int lastSlash = username.lastIndexOf('\\');
		if (lastSlash > -1) {
			username = username.substring(lastSlash + 1);
		}

		return username;
	}

	// From: https://www.owasp.org/index.php/Preventing_LDAP_Injection_in_Java
	public static final String escapeLDAPSearchFilter(String filter) {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < filter.length(); i++) {
			final char curChar = filter.charAt(i);
			switch (curChar) {
			case '\\':
				sb.append("\\5c");
				break;
			case '*':
				sb.append("\\2a");
				break;
			case '(':
				sb.append("\\28");
				break;
			case ')':
				sb.append("\\29");
				break;
			case '\u0000':
				sb.append("\\00");
				break;
			default:
				sb.append(curChar);
			}
		}
		return sb.toString();
	}

	private void configureSyncService() {
		final LdapSyncService ldapSyncService = new LdapSyncService(this.settings, this);
		if (ldapSyncService.isReady()) {
			final long ldapSyncPeriod = getSynchronizationPeriodInMilliseconds();
			final int delay = 1;
			this.logger.info("Ldap sync service will update users and groups every {} minutes.",
					TimeUnit.MILLISECONDS.toMinutes(ldapSyncPeriod));
			this.scheduledExecutorService.scheduleAtFixedRate(ldapSyncService, delay,
					ldapSyncPeriod, TimeUnit.MILLISECONDS);
		} else {
			this.logger.info("Ldap sync service is disabled.");
		}
	}

}
