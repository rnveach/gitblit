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
package com.gitblit.client;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.Constants.PermissionType;
import com.gitblit.Constants.RegistrantType;
import com.gitblit.Keys;
import com.gitblit.models.FederationModel;
import com.gitblit.models.FeedEntryModel;
import com.gitblit.models.FeedModel;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.ServerStatus;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.RpcUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.SyndicationUtils;

/**
 * GitblitClient is a object that retrieves data from a Gitblit server, caches
 * it for local operations, and allows updating or creating Gitblit objects.
 *
 * @author James Moger
 *
 */
public class GitblitClient implements Serializable {

	private static final long serialVersionUID = 1L;

	private static final Date NEVER = new Date(0);

	protected final GitblitRegistration reg;

	public final String url;

	public final String account;

	private final char[] password;

	private volatile int protocolVersion;

	private volatile boolean allowManagement;

	private volatile boolean allowAdministration;

	private volatile ServerSettings settings;

	private final List<RepositoryModel> allRepositories;

	private final List<UserModel> allUsers;

	private final List<TeamModel> allTeams;

	private final List<FederationModel> federationRegistrations;

	private final List<FeedModel> availableFeeds;

	private final List<FeedEntryModel> syndicatedEntries;

	private final Set<String> subscribedRepositories;

	private ServerStatus status;

	public GitblitClient(GitblitRegistration reg) {
		this.reg = reg;
		this.url = reg.url;
		this.account = reg.account;
		this.password = reg.password;

		this.allUsers = new ArrayList<UserModel>();
		this.allTeams = new ArrayList<TeamModel>();
		this.allRepositories = new ArrayList<RepositoryModel>();
		this.federationRegistrations = new ArrayList<FederationModel>();
		this.availableFeeds = new ArrayList<FeedModel>();
		this.syndicatedEntries = new ArrayList<FeedEntryModel>();
		this.subscribedRepositories = new HashSet<String>();
	}

	public void login() throws IOException {
		this.protocolVersion = RpcUtils.getProtocolVersion(this.url, this.account, this.password);
		refreshSettings();
		refreshAvailableFeeds();
		refreshRepositories();
		refreshSubscribedFeeds(0);

		// credentials may not have administrator access
		// or server may have disabled rpc management
		refreshUsers();
		if (this.protocolVersion > 1) {
			refreshTeams();
		}
		this.allowManagement = true;

		// credentials may not have administrator access
		// or server may have disabled rpc administration
		refreshStatus();
		this.allowAdministration = true;

	}

	public int getProtocolVersion() {
		return this.protocolVersion;
	}

	public boolean allowManagement() {
		return this.allowManagement;
	}

	public boolean allowAdministration() {
		return this.allowAdministration;
	}

	public boolean isOwner(RepositoryModel model) {
		return model.isOwner(this.account);
	}

	public String getURL(String action, String repository, String objectId) {
		final boolean mounted = this.settings.get(Keys.web.mountParameters).getBoolean(true);
		final StringBuilder sb = new StringBuilder();
		sb.append(this.url);
		sb.append('/');
		sb.append(action);
		sb.append('/');
		if (mounted) {
			// mounted url/action/repository/objectId
			sb.append(StringUtils.encodeURL(repository));
			if (!StringUtils.isEmpty(objectId)) {
				sb.append('/');
				sb.append(objectId);
			}
			return sb.toString();
		} else {
			// parameterized url/action/&r=repository&h=objectId
			sb.append("?r=");
			sb.append(repository);
			if (!StringUtils.isEmpty(objectId)) {
				sb.append("&h=");
				sb.append(objectId);
			}
			return sb.toString();
		}
	}

	public AccessRestrictionType getDefaultAccessRestriction() {
		String restriction = "PUSH";
		if (this.settings.hasKey(Keys.git.defaultAccessRestriction)) {
			restriction = this.settings.get(Keys.git.defaultAccessRestriction).currentValue;
		}
		return AccessRestrictionType.fromName(restriction);
	}

	public AuthorizationControl getDefaultAuthorizationControl() {
		String authorization = null;
		if (this.settings.hasKey(Keys.git.defaultAuthorizationControl)) {
			authorization = this.settings.get(Keys.git.defaultAuthorizationControl).currentValue;
		}
		return AuthorizationControl.fromName(authorization);
	}

	/**
	 * Returns the list of pre-receive scripts the repository inherited from the
	 * global settings and team affiliations.
	 *
	 * @param repository
	 *            if null only the globally specified scripts are returned
	 * @return a list of scripts
	 */
	public List<String> getPreReceiveScriptsInherited(RepositoryModel repository) {
		final Set<String> scripts = new LinkedHashSet<String>();
		// Globals
		for (final String script : this.settings.get(Keys.groovy.preReceiveScripts).getStrings()) {
			if (script.endsWith(".groovy")) {
				scripts.add(script.substring(0, script.lastIndexOf('.')));
			} else {
				scripts.add(script);
			}
		}

		// Team Scripts
		if (repository != null) {
			for (final String teamname : getPermittedTeamnames(repository)) {
				final TeamModel team = getTeamModel(teamname);
				if (!ArrayUtils.isEmpty(team.preReceiveScripts)) {
					scripts.addAll(team.preReceiveScripts);
				}
			}
		}
		return new ArrayList<String>(scripts);
	}

	/**
	 * Returns the list of all available Groovy pre-receive push hook scripts
	 * that are not already inherited by the repository. Script files must have
	 * .groovy extension
	 *
	 * @param repository
	 *            optional parameter
	 * @return list of available hook scripts
	 */
	public List<String> getPreReceiveScriptsUnused(RepositoryModel repository) {
		final Set<String> inherited = new TreeSet<String>(getPreReceiveScriptsInherited(repository));

		// create list of available scripts by excluding inherited scripts
		final List<String> scripts = new ArrayList<String>();
		if (!ArrayUtils.isEmpty(this.settings.pushScripts)) {
			for (final String script : this.settings.pushScripts) {
				if (!inherited.contains(script)) {
					scripts.add(script);
				}
			}
		}
		return scripts;
	}

	/**
	 * Returns the list of post-receive scripts the repository inherited from
	 * the global settings and team affiliations.
	 *
	 * @param repository
	 *            if null only the globally specified scripts are returned
	 * @return a list of scripts
	 */
	public List<String> getPostReceiveScriptsInherited(RepositoryModel repository) {
		final Set<String> scripts = new LinkedHashSet<String>();
		// Global Scripts
		for (final String script : this.settings.get(Keys.groovy.postReceiveScripts).getStrings()) {
			if (script.endsWith(".groovy")) {
				scripts.add(script.substring(0, script.lastIndexOf('.')));
			} else {
				scripts.add(script);
			}
		}
		// Team Scripts
		if (repository != null) {
			for (final String teamname : getPermittedTeamnames(repository)) {
				final TeamModel team = getTeamModel(teamname);
				if (!ArrayUtils.isEmpty(team.postReceiveScripts)) {
					scripts.addAll(team.postReceiveScripts);
				}
			}
		}
		return new ArrayList<String>(scripts);
	}

	/**
	 * Returns the list of unused Groovy post-receive push hook scripts that are
	 * not already inherited by the repository. Script files must have .groovy
	 * extension
	 *
	 * @param repository
	 *            optional parameter
	 * @return list of available hook scripts
	 */
	public List<String> getPostReceiveScriptsUnused(RepositoryModel repository) {
		final Set<String> inherited = new TreeSet<String>(
				getPostReceiveScriptsInherited(repository));

		// create list of available scripts by excluding inherited scripts
		final List<String> scripts = new ArrayList<String>();
		if (!ArrayUtils.isEmpty(this.settings.pushScripts)) {
			for (final String script : this.settings.pushScripts) {
				if (!inherited.contains(script)) {
					scripts.add(script);
				}
			}
		}
		return scripts;
	}

	public ServerSettings getSettings() {
		return this.settings;
	}

	public ServerStatus getStatus() {
		return this.status;
	}

	public String getSettingDescription(String key) {
		return this.settings.get(key).description;
	}

	public List<RepositoryModel> refreshRepositories() throws IOException {
		final Map<String, RepositoryModel> repositories = RpcUtils.getRepositories(this.url,
				this.account, this.password);
		this.allRepositories.clear();
		this.allRepositories.addAll(repositories.values());
		Collections.sort(this.allRepositories);
		markSubscribedFeeds();
		return this.allRepositories;
	}

	public List<UserModel> refreshUsers() throws IOException {
		final List<UserModel> users = RpcUtils.getUsers(this.url, this.account, this.password);
		this.allUsers.clear();
		this.allUsers.addAll(users);
		Collections.sort(users);
		return this.allUsers;
	}

	public List<TeamModel> refreshTeams() throws IOException {
		final List<TeamModel> teams = RpcUtils.getTeams(this.url, this.account, this.password);
		this.allTeams.clear();
		this.allTeams.addAll(teams);
		Collections.sort(teams);
		return this.allTeams;
	}

	public ServerSettings refreshSettings() throws IOException {
		this.settings = RpcUtils.getSettings(this.url, this.account, this.password);
		return this.settings;
	}

	public ServerStatus refreshStatus() throws IOException {
		this.status = RpcUtils.getStatus(this.url, this.account, this.password);
		return this.status;
	}

	public List<String> getBranches(String repository) {
		final List<FeedModel> feeds = getAvailableFeeds(repository);
		final List<String> branches = new ArrayList<String>();
		for (final FeedModel feed : feeds) {
			branches.add(feed.branch);
		}
		Collections.sort(branches);
		return branches;
	}

	public List<FeedModel> getAvailableFeeds() {
		return this.availableFeeds;
	}

	public List<FeedModel> getAvailableFeeds(RepositoryModel repository) {
		return getAvailableFeeds(repository.name);
	}

	public List<FeedModel> getAvailableFeeds(String repository) {
		final List<FeedModel> repositoryFeeds = new ArrayList<FeedModel>();
		if (repository == null) {
			return repositoryFeeds;
		}
		for (final FeedModel feed : this.availableFeeds) {
			if (feed.repository.equalsIgnoreCase(repository)) {
				repositoryFeeds.add(feed);
			}
		}
		return repositoryFeeds;
	}

	public List<FeedModel> refreshAvailableFeeds() throws IOException {
		final List<FeedModel> feeds = RpcUtils
				.getBranchFeeds(this.url, this.account, this.password);
		this.availableFeeds.clear();
		this.availableFeeds.addAll(feeds);
		markSubscribedFeeds();
		return this.availableFeeds;
	}

	public List<FeedEntryModel> refreshSubscribedFeeds(int page) throws IOException {
		final Set<FeedEntryModel> allEntries = new HashSet<FeedEntryModel>();
		if (this.reg.feeds.size() > 0) {
			for (final FeedModel feed : this.reg.feeds) {
				feed.lastRefreshDate = feed.currentRefreshDate;
				feed.currentRefreshDate = new Date();
				final List<FeedEntryModel> entries = SyndicationUtils.readFeed(this.url,
						feed.repository, feed.branch, -1, page, this.account, this.password);
				allEntries.addAll(entries);
			}
		}
		this.reg.cacheFeeds();
		this.syndicatedEntries.clear();
		this.syndicatedEntries.addAll(allEntries);
		Collections.sort(this.syndicatedEntries);
		return this.syndicatedEntries;
	}

	public void updateSubscribedFeeds(List<FeedModel> list) {
		this.reg.updateSubscribedFeeds(list);
		markSubscribedFeeds();
	}

	private void markSubscribedFeeds() {
		this.subscribedRepositories.clear();
		for (final FeedModel feed : this.availableFeeds) {
			// mark feed in the available list as subscribed
			feed.subscribed = this.reg.feeds.contains(feed);
			if (feed.subscribed) {
				this.subscribedRepositories.add(feed.repository.toLowerCase());
			}
		}
	}

	public Date getLastFeedRefresh(String repository, String branch) {
		FeedModel feed = new FeedModel();
		feed.repository = repository;
		feed.branch = branch;
		if (this.reg.feeds.contains(feed)) {
			final int idx = this.reg.feeds.indexOf(feed);
			feed = this.reg.feeds.get(idx);
			return feed.lastRefreshDate;
		}
		return NEVER;
	}

	public boolean isSubscribed(RepositoryModel repository) {
		return this.subscribedRepositories.contains(repository.name.toLowerCase());
	}

	public List<FeedEntryModel> getSyndicatedEntries() {
		return this.syndicatedEntries;
	}

	public List<FeedEntryModel> log(String repository, String branch, int numberOfEntries, int page)
			throws IOException {
		return SyndicationUtils.readFeed(this.url, repository, branch, numberOfEntries, page,
				this.account, this.password);
	}

	public List<FeedEntryModel> search(String repository, String branch, String fragment,
			Constants.SearchType type, int numberOfEntries, int page) throws IOException {
		return SyndicationUtils.readSearchFeed(this.url, repository, branch, fragment, type,
				numberOfEntries, page, this.account, this.password);
	}

	public List<FederationModel> refreshFederationRegistrations() throws IOException {
		final List<FederationModel> list = RpcUtils.getFederationRegistrations(this.url,
				this.account, this.password);
		this.federationRegistrations.clear();
		this.federationRegistrations.addAll(list);
		return this.federationRegistrations;
	}

	public List<UserModel> getUsers() {
		return this.allUsers;
	}

	public UserModel getUser(String username) {
		for (final UserModel user : getUsers()) {
			if (user.username.equalsIgnoreCase(username)) {
				return user;
			}
		}
		return null;
	}

	public List<String> getUsernames() {
		final List<String> usernames = new ArrayList<String>();
		for (final UserModel user : this.allUsers) {
			usernames.add(user.username);
		}
		Collections.sort(usernames);
		return usernames;
	}

	public List<String> getPermittedUsernames(RepositoryModel repository) {
		final List<String> usernames = new ArrayList<String>();
		for (final UserModel user : this.allUsers) {
			if (user.hasRepositoryPermission(repository.name)) {
				usernames.add(user.username);
			}
		}
		return usernames;
	}

	/**
	 * Returns the effective list of permissions for this user, taking into
	 * account team memberships, ownerships.
	 *
	 * @param user
	 * @return the effective list of permissions for the user
	 */
	public List<RegistrantAccessPermission> getUserAccessPermissions(UserModel user) {
		final Set<RegistrantAccessPermission> set = new LinkedHashSet<RegistrantAccessPermission>();
		set.addAll(user.getRepositoryPermissions());
		// Flag missing repositories
		for (final RegistrantAccessPermission permission : set) {
			if (permission.mutable && PermissionType.EXPLICIT.equals(permission.permissionType)) {
				final RepositoryModel rm = getRepository(permission.registrant);
				if (rm == null) {
					permission.permissionType = PermissionType.MISSING;
					permission.mutable = false;
					continue;
				}
			}
		}

		// TODO reconsider ownership as a user property
		// manually specify personal repository ownerships
		for (final RepositoryModel rm : this.allRepositories) {
			if (rm.isUsersPersonalRepository(user.username) || rm.isOwner(user.username)) {
				final RegistrantAccessPermission rp = new RegistrantAccessPermission(rm.name,
						AccessPermission.REWIND, PermissionType.OWNER, RegistrantType.REPOSITORY,
						null, false);
				// user may be owner of a repository to which they've inherited
				// a team permission, replace any existing perm with owner perm
				set.remove(rp);
				set.add(rp);
			}
		}

		final List<RegistrantAccessPermission> list = new ArrayList<RegistrantAccessPermission>(set);
		Collections.sort(list);
		return list;
	}

	public List<RegistrantAccessPermission> getUserAccessPermissions(RepositoryModel repository) {
		final List<RegistrantAccessPermission> list = new ArrayList<RegistrantAccessPermission>();
		if (AccessRestrictionType.NONE.equals(repository.accessRestriction)) {
			// no permissions needed, REWIND for everyone!
			return list;
		}
		if (AuthorizationControl.AUTHENTICATED.equals(repository.authorizationControl)) {
			// no permissions needed, REWIND for authenticated!
			return list;
		}
		// NAMED users and teams
		for (final UserModel user : this.allUsers) {
			final RegistrantAccessPermission ap = user.getRepositoryPermission(repository);
			if (ap.permission.exceeds(AccessPermission.NONE)) {
				list.add(ap);
			}
		}
		return list;
	}

	public boolean setUserAccessPermissions(RepositoryModel repository,
			List<RegistrantAccessPermission> permissions) throws IOException {
		return RpcUtils.setRepositoryMemberPermissions(repository, permissions, this.url,
				this.account, this.password);
	}

	public List<TeamModel> getTeams() {
		return this.allTeams;
	}

	public List<String> getTeamnames() {
		final List<String> teamnames = new ArrayList<String>();
		for (final TeamModel team : this.allTeams) {
			teamnames.add(team.name);
		}
		Collections.sort(teamnames);
		return teamnames;
	}

	public List<String> getPermittedTeamnames(RepositoryModel repository) {
		final List<String> teamnames = new ArrayList<String>();
		for (final TeamModel team : this.allTeams) {
			if (team.hasRepositoryPermission(repository.name)) {
				teamnames.add(team.name);
			}
		}
		return teamnames;
	}

	public List<RegistrantAccessPermission> getTeamAccessPermissions(RepositoryModel repository) {
		final List<RegistrantAccessPermission> list = new ArrayList<RegistrantAccessPermission>();
		for (final TeamModel team : this.allTeams) {
			final RegistrantAccessPermission ap = team.getRepositoryPermission(repository);
			if (ap.permission.exceeds(AccessPermission.NONE)) {
				list.add(ap);
			}
		}
		Collections.sort(list);
		return list;
	}

	public boolean setTeamAccessPermissions(RepositoryModel repository,
			List<RegistrantAccessPermission> permissions) throws IOException {
		return RpcUtils.setRepositoryTeamPermissions(repository, permissions, this.url,
				this.account, this.password);
	}

	public TeamModel getTeamModel(String name) {
		for (final TeamModel team : this.allTeams) {
			if (team.name.equalsIgnoreCase(name)) {
				return team;
			}
		}
		return null;
	}

	public List<String> getFederationSets() {
		return this.settings.get(Keys.federation.sets).getStrings();
	}

	public List<RepositoryModel> getRepositories() {
		return this.allRepositories;
	}

	public RepositoryModel getRepository(String name) {
		for (final RepositoryModel repository : this.allRepositories) {
			if (repository.name.equalsIgnoreCase(name)) {
				return repository;
			}
		}
		return null;
	}

	public boolean createRepository(RepositoryModel repository,
			List<RegistrantAccessPermission> userPermissions) throws IOException {
		return createRepository(repository, userPermissions, null);
	}

	public boolean createRepository(RepositoryModel repository,
			List<RegistrantAccessPermission> userPermissions,
			List<RegistrantAccessPermission> teamPermissions) throws IOException {
		boolean success = true;
		success &= RpcUtils.createRepository(repository, this.url, this.account, this.password);
		if ((userPermissions != null) && (userPermissions.size() > 0)) {
			// if new repository has named members, set them
			success &= RpcUtils.setRepositoryMemberPermissions(repository, userPermissions,
					this.url, this.account, this.password);
		}
		if ((teamPermissions != null) && (teamPermissions.size() > 0)) {
			// if new repository has named teams, set them
			success &= RpcUtils.setRepositoryTeamPermissions(repository, teamPermissions, this.url,
					this.account, this.password);
		}
		return success;
	}

	public boolean updateRepository(String name, RepositoryModel repository,
			List<RegistrantAccessPermission> userPermissions) throws IOException {
		return updateRepository(name, repository, userPermissions, null);
	}

	public boolean updateRepository(String name, RepositoryModel repository,
			List<RegistrantAccessPermission> userPermissions,
			List<RegistrantAccessPermission> teamPermissions) throws IOException {
		boolean success = true;
		success &= RpcUtils.updateRepository(name, repository, this.url, this.account,
				this.password);
		// set the repository members
		if (userPermissions != null) {
			success &= RpcUtils.setRepositoryMemberPermissions(repository, userPermissions,
					this.url, this.account, this.password);
		}
		if (teamPermissions != null) {
			success &= RpcUtils.setRepositoryTeamPermissions(repository, teamPermissions, this.url,
					this.account, this.password);
		}
		return success;
	}

	public boolean deleteRepository(RepositoryModel repository) throws IOException {
		return RpcUtils.deleteRepository(repository, this.url, this.account, this.password);
	}

	public boolean clearRepositoryCache() throws IOException {
		return RpcUtils.clearRepositoryCache(this.url, this.account, this.password);
	}

	public boolean createUser(UserModel user) throws IOException {
		return RpcUtils.createUser(user, this.url, this.account, this.password);
	}

	public boolean updateUser(String name, UserModel user) throws IOException {
		return RpcUtils.updateUser(name, user, this.url, this.account, this.password);
	}

	public boolean deleteUser(UserModel user) throws IOException {
		return RpcUtils.deleteUser(user, this.url, this.account, this.password);
	}

	public boolean createTeam(TeamModel team) throws IOException {
		return RpcUtils.createTeam(team, this.url, this.account, this.password);
	}

	public boolean updateTeam(String name, TeamModel team) throws IOException {
		return RpcUtils.updateTeam(name, team, this.url, this.account, this.password);
	}

	public boolean deleteTeam(TeamModel team) throws IOException {
		return RpcUtils.deleteTeam(team, this.url, this.account, this.password);
	}

	public boolean updateSettings(Map<String, String> newSettings) throws IOException {
		return RpcUtils.updateSettings(newSettings, this.url, this.account, this.password);
	}
}
