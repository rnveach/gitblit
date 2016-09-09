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

import java.nio.charset.Charset;
import java.security.Principal;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.Constants.AccountType;
import com.gitblit.Constants.AuthenticationType;
import com.gitblit.Constants.Role;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.auth.AuthenticationProvider;
import com.gitblit.auth.AuthenticationProvider.UsernamePasswordAuthenticationProvider;
import com.gitblit.auth.HtpasswdAuthProvider;
import com.gitblit.auth.HttpHeaderAuthProvider;
import com.gitblit.auth.LdapAuthProvider;
import com.gitblit.auth.PAMAuthProvider;
import com.gitblit.auth.RedmineAuthProvider;
import com.gitblit.auth.SalesforceAuthProvider;
import com.gitblit.auth.WindowsAuthProvider;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.SshKey;
import com.gitblit.utils.Base64;
import com.gitblit.utils.HttpUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.X509Utils.X509Metadata;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * The authentication manager handles user login & logout.
 *
 * @author James Moger
 *
 */
@Singleton
public class AuthenticationManager implements IAuthenticationManager {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final IStoredSettings settings;

	private final IRuntimeManager runtimeManager;

	private final IUserManager userManager;

	private final List<AuthenticationProvider> authenticationProviders;

	private final Map<String, Class<? extends AuthenticationProvider>> providerNames;

	private final Map<String, String> legacyRedirects;

	@Inject
	public AuthenticationManager(IRuntimeManager runtimeManager, IUserManager userManager) {

		this.settings = runtimeManager.getSettings();
		this.runtimeManager = runtimeManager;
		this.userManager = userManager;
		this.authenticationProviders = new ArrayList<AuthenticationProvider>();

		// map of shortcut provider names
		this.providerNames = new HashMap<String, Class<? extends AuthenticationProvider>>();
		this.providerNames.put("htpasswd", HtpasswdAuthProvider.class);
		this.providerNames.put("httpheader", HttpHeaderAuthProvider.class);
		this.providerNames.put("ldap", LdapAuthProvider.class);
		this.providerNames.put("pam", PAMAuthProvider.class);
		this.providerNames.put("redmine", RedmineAuthProvider.class);
		this.providerNames.put("salesforce", SalesforceAuthProvider.class);
		this.providerNames.put("windows", WindowsAuthProvider.class);

		// map of legacy external user services
		this.legacyRedirects = new HashMap<String, String>();
		this.legacyRedirects.put("com.gitblit.HtpasswdUserService", "htpasswd");
		this.legacyRedirects.put("com.gitblit.LdapUserService", "ldap");
		this.legacyRedirects.put("com.gitblit.PAMUserService", "pam");
		this.legacyRedirects.put("com.gitblit.RedmineUserService", "redmine");
		this.legacyRedirects.put("com.gitblit.SalesforceUserService", "salesforce");
		this.legacyRedirects.put("com.gitblit.WindowsUserService", "windows");
	}

	@Override
	public AuthenticationManager start() {
		// automatically adjust legacy configurations
		final String realm = this.settings.getString(Keys.realm.userService,
				"${baseFolder}/users.conf");
		if (this.legacyRedirects.containsKey(realm)) {
			this.logger.warn("");
			this.logger.warn(Constants.BORDER2);
			this.logger.warn(" IUserService '{}' is obsolete!", realm);
			this.logger.warn(" Please set '{}={}'", "realm.authenticationProviders",
					this.legacyRedirects.get(realm));
			this.logger.warn(Constants.BORDER2);
			this.logger.warn("");

			// conditionally override specified authentication providers
			if (StringUtils.isEmpty(this.settings.getString(Keys.realm.authenticationProviders,
					null))) {
				this.settings.overrideSetting(Keys.realm.authenticationProviders,
						this.legacyRedirects.get(realm));
			}
		}

		// instantiate and setup specified authentication providers
		final List<String> providers = this.settings.getStrings(Keys.realm.authenticationProviders);
		if (providers.isEmpty()) {
			this.logger.info("External authentication disabled.");
		} else {
			for (final String provider : providers) {
				try {
					Class<?> authClass;
					if (this.providerNames.containsKey(provider)) {
						// map the name -> class
						authClass = this.providerNames.get(provider);
					} else {
						// reflective lookup
						authClass = Class.forName(provider);
					}
					this.logger.info("setting up {}", authClass.getName());
					final AuthenticationProvider authImpl = (AuthenticationProvider) authClass
							.newInstance();
					authImpl.setup(this.runtimeManager, this.userManager);
					this.authenticationProviders.add(authImpl);
				}
				catch (final Exception e) {
					this.logger.error("", e);
				}
			}
		}
		return this;
	}

	@Override
	public AuthenticationManager stop() {
		for (final AuthenticationProvider provider : this.authenticationProviders) {
			try {
				provider.stop();
			}
			catch (final Exception e) {
				this.logger.error("Failed to stop " + provider.getClass().getSimpleName(), e);
			}
		}
		return this;
	}

	public void addAuthenticationProvider(AuthenticationProvider prov) {
		this.authenticationProviders.add(prov);
	}

	/**
	 * Used to handle authentication for page requests.
	 *
	 * This allows authentication to occur based on the contents of the request
	 * itself. If no configured @{AuthenticationProvider}s authenticate
	 * succesffully, a request for login will be shown.
	 *
	 * Authentication by X509Certificate is tried first and then by cookie.
	 *
	 * @param httpRequest
	 * @return a user object or null
	 */
	@Override
	public UserModel authenticate(HttpServletRequest httpRequest) {
		return authenticate(httpRequest, false);
	}

	/**
	 * Authenticate a user based on HTTP request parameters.
	 *
	 * Authentication by custom HTTP header, servlet container principal,
	 * X509Certificate, cookie, and finally BASIC header.
	 *
	 * @param httpRequest
	 * @param requiresCertificate
	 * @return a user object or null
	 */
	@Override
	public UserModel authenticate(HttpServletRequest httpRequest, boolean requiresCertificate) {

		// Check if this request has already been authenticated, and trust that
		// instead of re-processing
		final String reqAuthUser = (String) httpRequest.getAttribute(Constants.ATTRIB_AUTHUSER);
		if (!StringUtils.isEmpty(reqAuthUser)) {
			this.logger.debug("Called servlet authenticate when request is already authenticated.");
			return this.userManager.getUserModel(reqAuthUser);
		}

		// try to authenticate by servlet container principal
		if (!requiresCertificate) {
			final Principal principal = httpRequest.getUserPrincipal();
			if (principal != null) {
				final String username = principal.getName();
				if (!StringUtils.isEmpty(username)) {
					final boolean internalAccount = this.userManager.isInternalAccount(username);
					UserModel user = this.userManager.getUserModel(username);
					if (user != null) {
						// existing user
						flagRequest(httpRequest, AuthenticationType.CONTAINER, user.username);
						this.logger.debug(MessageFormat.format(
								"{0} authenticated by servlet container principal from {1}",
								user.username, httpRequest.getRemoteAddr()));
						return validateAuthentication(user, AuthenticationType.CONTAINER);
					} else if (this.settings.getBoolean(Keys.realm.container.autoCreateAccounts,
							false) && !internalAccount) {
						// auto-create user from an authenticated container
						// principal
						user = new UserModel(username.toLowerCase());
						user.displayName = username;
						user.password = Constants.EXTERNAL_ACCOUNT;
						user.accountType = AccountType.CONTAINER;

						// Try to extract user's informations for the session
						// it uses "realm.container.autoAccounts.*" as the
						// attribute name to look for
						final HttpSession session = httpRequest.getSession();
						final String emailAddress = resolveAttribute(session,
								Keys.realm.container.autoAccounts.emailAddress);
						if (emailAddress != null) {
							user.emailAddress = emailAddress;
						}
						final String displayName = resolveAttribute(session,
								Keys.realm.container.autoAccounts.displayName);
						if (displayName != null) {
							user.displayName = displayName;
						}
						final String userLocale = resolveAttribute(session,
								Keys.realm.container.autoAccounts.locale);
						if (userLocale != null) {
							user.getPreferences().setLocale(userLocale);
						}
						final String adminRole = this.settings.getString(
								Keys.realm.container.autoAccounts.adminRole, null);
						if ((adminRole != null) && !adminRole.isEmpty()) {
							if (httpRequest.isUserInRole(adminRole)) {
								user.canAdmin = true;
							}
						}

						this.userManager.updateUserModel(user);
						flagRequest(httpRequest, AuthenticationType.CONTAINER, user.username);
						this.logger
								.debug(MessageFormat
										.format("{0} authenticated and created by servlet container principal from {1}",
												user.username, httpRequest.getRemoteAddr()));
						return validateAuthentication(user, AuthenticationType.CONTAINER);
					} else if (!internalAccount) {
						this.logger
								.warn(MessageFormat
										.format("Failed to find UserModel for {0}, attempted servlet container authentication from {1}",
												principal.getName(), httpRequest.getRemoteAddr()));
					}
				}
			}
		}

		// try to authenticate by certificate
		final boolean checkValidity = this.settings.getBoolean(Keys.git.enforceCertificateValidity,
				true);
		final String[] oids = this.settings.getStrings(Keys.git.certificateUsernameOIDs).toArray(
				new String[0]);
		final UserModel model = HttpUtils.getUserModelFromCertificate(httpRequest, checkValidity,
				oids);
		if (model != null) {
			// grab real user model and preserve certificate serial number
			final UserModel user = this.userManager.getUserModel(model.username);
			final X509Metadata metadata = HttpUtils.getCertificateMetadata(httpRequest);
			if (user != null) {
				flagRequest(httpRequest, AuthenticationType.CERTIFICATE, user.username);
				this.logger.debug(MessageFormat.format(
						"{0} authenticated by client certificate {1} from {2}", user.username,
						metadata.serialNumber, httpRequest.getRemoteAddr()));
				return validateAuthentication(user, AuthenticationType.CERTIFICATE);
			} else {
				this.logger
						.warn(MessageFormat
								.format("Failed to find UserModel for {0}, attempted client certificate ({1}) authentication from {2}",
										model.username, metadata.serialNumber,
										httpRequest.getRemoteAddr()));
			}
		}

		if (requiresCertificate) {
			// caller requires client certificate authentication (e.g. git
			// servlet)
			return null;
		}

		UserModel user = null;

		// try to authenticate by cookie
		final String cookie = getCookie(httpRequest);
		if (!StringUtils.isEmpty(cookie)) {
			user = this.userManager.getUserModel(cookie.toCharArray());
			if (user != null) {
				flagRequest(httpRequest, AuthenticationType.COOKIE, user.username);
				this.logger.debug(MessageFormat.format("{0} authenticated by cookie from {1}",
						user.username, httpRequest.getRemoteAddr()));
				return validateAuthentication(user, AuthenticationType.COOKIE);
			}
		}

		// try to authenticate by BASIC
		final String authorization = httpRequest.getHeader("Authorization");
		if ((authorization != null) && authorization.startsWith("Basic")) {
			// Authorization: Basic base64credentials
			final String base64Credentials = authorization.substring("Basic".length()).trim();
			final String credentials = new String(Base64.decode(base64Credentials),
					Charset.forName("UTF-8"));
			// credentials = username:password
			final String[] values = credentials.split(":", 2);

			if (values.length == 2) {
				final String username = values[0];
				final char[] password = values[1].toCharArray();
				user = authenticate(username, password, httpRequest.getRemoteAddr());
				if (user != null) {
					flagRequest(httpRequest, AuthenticationType.CREDENTIALS, user.username);
					this.logger.debug(MessageFormat.format(
							"{0} authenticated by BASIC request header from {1}", user.username,
							httpRequest.getRemoteAddr()));
					return validateAuthentication(user, AuthenticationType.CREDENTIALS);
				}
			}
		}

		// Check each configured AuthenticationProvider
		for (final AuthenticationProvider ap : this.authenticationProviders) {
			final UserModel authedUser = ap.authenticate(httpRequest);
			if (null != authedUser) {
				flagRequest(httpRequest, ap.getAuthenticationType(), authedUser.username);
				this.logger.debug(MessageFormat.format("{0} authenticated by {1} from {2} for {3}",
						authedUser.username, ap.getServiceName(), httpRequest.getRemoteAddr(),
						httpRequest.getPathInfo()));
				return validateAuthentication(authedUser, ap.getAuthenticationType());
			}
		}
		return null;
	}

	/**
	 * Extract given attribute from the session and return it's content it
	 * return null if attributeMapping is empty, or if the value is empty
	 * 
	 * @param session
	 *            The user session
	 * @param attributeMapping
	 * @return
	 */
	private String resolveAttribute(HttpSession session, String attributeMapping) {
		final String attributeName = this.settings.getString(attributeMapping, null);
		if (StringUtils.isEmpty(attributeName)) {
			return null;
		}
		final Object attributeValue = session.getAttribute(attributeName);
		if (attributeValue == null) {
			return null;
		}
		final String value = attributeValue.toString();
		if (value.isEmpty()) {
			return null;
		}
		return value;
	}

	/**
	 * Authenticate a user based on a public key.
	 *
	 * This implementation assumes that the authentication has already take
	 * place (e.g. SSHDaemon) and that this is a validation/verification of the
	 * user.
	 *
	 * @param username
	 * @param key
	 * @return a user object or null
	 */
	@Override
	public UserModel authenticate(String username, SshKey key) {
		if (username != null) {
			if (!StringUtils.isEmpty(username)) {
				final UserModel user = this.userManager.getUserModel(username);
				if (user != null) {
					// existing user
					this.logger.debug(MessageFormat.format("{0} authenticated by {1} public key",
							user.username, key.getAlgorithm()));
					return validateAuthentication(user, AuthenticationType.PUBLIC_KEY);
				}
				this.logger.warn(MessageFormat.format(
						"Failed to find UserModel for {0} during public key authentication",
						username));
			}
		} else {
			this.logger.warn("Empty user passed to AuthenticationManager.authenticate!");
		}
		return null;
	}

	/**
	 * Return the UserModel for already authenticated user.
	 *
	 * This implementation assumes that the authentication has already take
	 * place (e.g. SSHDaemon) and that this is a validation/verification of the
	 * user.
	 *
	 * @param username
	 * @return a user object or null
	 */
	@Override
	public UserModel authenticate(String username) {
		if (username != null) {
			if (!StringUtils.isEmpty(username)) {
				final UserModel user = this.userManager.getUserModel(username);
				if (user != null) {
					// existing user
					this.logger.debug(MessageFormat.format("{0} authenticated externally",
							user.username));
					return validateAuthentication(user, AuthenticationType.CONTAINER);
				}
				this.logger.warn(MessageFormat
						.format("Failed to find UserModel for {0} during external authentication",
								username));
			}
		} else {
			this.logger.warn("Empty user passed to AuthenticationManager.authenticate!");
		}
		return null;
	}

	/**
	 * This method allows the authentication manager to reject authentication
	 * attempts. It is called after the username/secret have been verified to
	 * ensure that the authentication technique has been logged.
	 *
	 * @param user
	 * @return
	 */
	protected UserModel validateAuthentication(UserModel user, AuthenticationType type) {
		if (user == null) {
			return null;
		}
		if (user.disabled) {
			// user has been disabled
			this.logger.warn("Rejected {} authentication attempt by disabled account \"{}\"", type,
					user.username);
			return null;
		}
		return user;
	}

	protected void flagRequest(HttpServletRequest httpRequest,
			AuthenticationType authenticationType, String authedUsername) {
		httpRequest.setAttribute(Constants.ATTRIB_AUTHUSER, authedUsername);
		httpRequest.setAttribute(Constants.ATTRIB_AUTHTYPE, authenticationType);
	}

	/**
	 * Authenticate a user based on a username and password.
	 *
	 * @see IUserService.authenticate(String, char[])
	 * @param username
	 * @param password
	 * @return a user object or null
	 */
	@Override
	public UserModel authenticate(String username, char[] password, String remoteIP) {
		if (StringUtils.isEmpty(username)) {
			// can not authenticate empty username
			return null;
		}

		if (username.equalsIgnoreCase(Constants.FEDERATION_USER)) {
			// can not authenticate internal FEDERATION_USER at this point
			// it must be routed to FederationManager
			return null;
		}

		final String usernameDecoded = StringUtils.decodeUsername(username);
		final String pw = new String(password);
		if (StringUtils.isEmpty(pw)) {
			// can not authenticate empty password
			return null;
		}

		final UserModel user = this.userManager.getUserModel(usernameDecoded);

		// try local authentication
		if ((user != null) && user.isLocalAccount()) {
			final UserModel returnedUser = authenticateLocal(user, password);
			if (returnedUser != null) {
				// user authenticated
				return returnedUser;
			}
		} else {
			// try registered external authentication providers
			for (final AuthenticationProvider provider : this.authenticationProviders) {
				if (provider instanceof UsernamePasswordAuthenticationProvider) {
					final UserModel returnedUser = provider.authenticate(usernameDecoded, password);
					if (returnedUser != null) {
						// user authenticated
						returnedUser.accountType = provider.getAccountType();
						return validateAuthentication(returnedUser, AuthenticationType.CREDENTIALS);
					}
				}
			}
		}

		// could not authenticate locally or with a provider
		this.logger.warn(MessageFormat.format(
				"Failed login attempt for {0}, invalid credentials from {1}", username,
				remoteIP != null ? remoteIP : "unknown"));

		return null;
	}

	/**
	 * Returns a UserModel if local authentication succeeds.
	 *
	 * @param user
	 * @param password
	 * @return a UserModel if local authentication succeeds, null otherwise
	 */
	protected UserModel authenticateLocal(UserModel user, char[] password) {
		UserModel returnedUser = null;
		if (user.password.startsWith(StringUtils.MD5_TYPE)) {
			// password digest
			final String md5 = StringUtils.MD5_TYPE + StringUtils.getMD5(new String(password));
			if (user.password.equalsIgnoreCase(md5)) {
				returnedUser = user;
			}
		} else if (user.password.startsWith(StringUtils.COMBINED_MD5_TYPE)) {
			// username+password digest
			final String md5 = StringUtils.COMBINED_MD5_TYPE
					+ StringUtils.getMD5(user.username.toLowerCase() + new String(password));
			if (user.password.equalsIgnoreCase(md5)) {
				returnedUser = user;
			}
		} else if (user.password.equals(new String(password))) {
			// plain-text password
			returnedUser = user;
		}
		return validateAuthentication(returnedUser, AuthenticationType.CREDENTIALS);
	}

	/**
	 * Returns the Gitlbit cookie in the request.
	 *
	 * @param request
	 * @return the Gitblit cookie for the request or null if not found
	 */
	@Override
	public String getCookie(HttpServletRequest request) {
		if (this.settings.getBoolean(Keys.web.allowCookieAuthentication, true)) {
			final Cookie[] cookies = request.getCookies();
			if ((cookies != null) && (cookies.length > 0)) {
				for (final Cookie cookie : cookies) {
					if (cookie.getName().equals(Constants.NAME)) {
						final String value = cookie.getValue();
						return value;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Sets a cookie for the specified user.
	 *
	 * @param response
	 * @param user
	 */
	@Override
	@Deprecated
	public void setCookie(HttpServletResponse response, UserModel user) {
		setCookie(null, response, user);
	}

	/**
	 * Sets a cookie for the specified user.
	 *
	 * @param request
	 * @param response
	 * @param user
	 */
	@Override
	public void setCookie(HttpServletRequest request, HttpServletResponse response, UserModel user) {
		if (this.settings.getBoolean(Keys.web.allowCookieAuthentication, true)) {
			boolean standardLogin = true;

			if (null != request) {
				// Pull the auth type from the request, it is set there if
				// container managed
				final AuthenticationType authenticationType = (AuthenticationType) request
						.getAttribute(Constants.ATTRIB_AUTHTYPE);

				if (null != authenticationType) {
					standardLogin = authenticationType.isStandard();
				}
			}

			if (standardLogin) {
				Cookie userCookie;
				if (user == null) {
					// clear cookie for logout
					userCookie = new Cookie(Constants.NAME, "");
				} else {
					// set cookie for login
					final String cookie = this.userManager.getCookie(user);
					if (StringUtils.isEmpty(cookie)) {
						// create empty cookie
						userCookie = new Cookie(Constants.NAME, "");
					} else {
						// create real cookie
						userCookie = new Cookie(Constants.NAME, cookie);
						// expire the cookie in 7 days
						userCookie.setMaxAge((int) TimeUnit.DAYS.toSeconds(7));
					}
				}
				String path = "/";
				if (request != null) {
					if (!StringUtils.isEmpty(request.getContextPath())) {
						path = request.getContextPath();
					}
				}
				userCookie.setPath(path);
				response.addCookie(userCookie);
			}
		}
	}

	/**
	 * Logout a user.
	 *
	 * @param response
	 * @param user
	 */
	@Override
	@Deprecated
	public void logout(HttpServletResponse response, UserModel user) {
		setCookie(null, response, null);
	}

	/**
	 * Logout a user.
	 *
	 * @param request
	 * @param response
	 * @param user
	 */
	@Override
	public void logout(HttpServletRequest request, HttpServletResponse response, UserModel user) {
		setCookie(request, response, null);
	}

	/**
	 * Returns true if the user's credentials can be changed.
	 *
	 * @param user
	 * @return true if the user service supports credential changes
	 */
	@Override
	public boolean supportsCredentialChanges(UserModel user) {
		return ((user != null) && user.isLocalAccount())
				|| findProvider(user).supportsCredentialChanges();
	}

	/**
	 * Returns true if the user's display name can be changed.
	 *
	 * @param user
	 * @return true if the user service supports display name changes
	 */
	@Override
	public boolean supportsDisplayNameChanges(UserModel user) {
		return ((user != null) && user.isLocalAccount())
				|| findProvider(user).supportsDisplayNameChanges();
	}

	/**
	 * Returns true if the user's email address can be changed.
	 *
	 * @param user
	 * @return true if the user service supports email address changes
	 */
	@Override
	public boolean supportsEmailAddressChanges(UserModel user) {
		return ((user != null) && user.isLocalAccount())
				|| findProvider(user).supportsEmailAddressChanges();
	}

	/**
	 * Returns true if the user's team memberships can be changed.
	 *
	 * @param user
	 * @return true if the user service supports team membership changes
	 */
	@Override
	public boolean supportsTeamMembershipChanges(UserModel user) {
		return ((user != null) && user.isLocalAccount())
				|| findProvider(user).supportsTeamMembershipChanges();
	}

	/**
	 * Returns true if the team memberships can be changed.
	 *
	 * @param user
	 * @return true if the team membership can be changed
	 */
	@Override
	public boolean supportsTeamMembershipChanges(TeamModel team) {
		return ((team != null) && team.isLocalTeam())
				|| findProvider(team).supportsTeamMembershipChanges();
	}

	/**
	 * Returns true if the user's role can be changed.
	 *
	 * @param user
	 * @return true if the user's role can be changed
	 */
	@Override
	public boolean supportsRoleChanges(UserModel user, Role role) {
		return ((user != null) && user.isLocalAccount())
				|| findProvider(user).supportsRoleChanges(user, role);
	}

	/**
	 * Returns true if the team's role can be changed.
	 *
	 * @param user
	 * @return true if the team's role can be changed
	 */
	@Override
	public boolean supportsRoleChanges(TeamModel team, Role role) {
		return ((team != null) && team.isLocalTeam())
				|| findProvider(team).supportsRoleChanges(team, role);
	}

	protected AuthenticationProvider findProvider(UserModel user) {
		for (final AuthenticationProvider provider : this.authenticationProviders) {
			if (provider.getAccountType().equals(user.accountType)) {
				return provider;
			}
		}
		return AuthenticationProvider.NULL_PROVIDER;
	}

	protected AuthenticationProvider findProvider(TeamModel team) {
		for (final AuthenticationProvider provider : this.authenticationProviders) {
			if (provider.getAccountType().equals(team.accountType)) {
				return provider;
			}
		}
		return AuthenticationProvider.NULL_PROVIDER;
	}
}
