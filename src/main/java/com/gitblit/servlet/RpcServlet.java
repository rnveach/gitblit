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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.Repository;

import com.gitblit.Constants;
import com.gitblit.Constants.RpcRequest;
import com.gitblit.GitBlitException;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IGitblit;
import com.gitblit.models.RefModel;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.DeepCopier;
import com.gitblit.utils.HttpUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.RpcUtils;
import com.gitblit.utils.StringUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Handles remote procedure calls.
 *
 * @author James Moger
 */
@Singleton
public class RpcServlet extends JsonServlet {

	private static final long serialVersionUID = 1L;

	public static final int PROTOCOL_VERSION = 8;

	private final IStoredSettings settings;

	private final IGitblit gitblit;

	@Inject
	public RpcServlet(IStoredSettings settings, IGitblit gitblit) {
		this.settings = settings;
		this.gitblit = gitblit;
	}

	/**
	 * Processes an rpc request.
	 *
	 * @param request
	 * @param response
	 * @throws javax.servlet.ServletException
	 * @throws java.io.IOException
	 */
	@Override
	protected void processRequest(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		final RpcRequest reqType = RpcRequest.fromName(request.getParameter("req"));
		final String objectName = request.getParameter("name");
		this.logger.info(MessageFormat.format("Rpc {0} request from {1}", reqType,
				request.getRemoteAddr()));

		final UserModel user = (UserModel) request.getUserPrincipal();

		final boolean allowManagement = (user != null) && user.canAdmin()
				&& this.settings.getBoolean(Keys.web.enableRpcManagement, false);

		final boolean allowAdmin = (user != null) && user.canAdmin()
				&& this.settings.getBoolean(Keys.web.enableRpcAdministration, false);

		Object result = null;
		if (RpcRequest.GET_PROTOCOL.equals(reqType)) {
			// Return the protocol version
			result = PROTOCOL_VERSION;
		} else if (RpcRequest.LIST_REPOSITORIES.equals(reqType)) {
			// Determine the Gitblit clone url
			String gitblitUrl = this.settings.getString(Keys.web.canonicalUrl, null);
			if (StringUtils.isEmpty(gitblitUrl)) {
				gitblitUrl = HttpUtils.getGitblitURL(request);
			}
			final StringBuilder sb = new StringBuilder();
			sb.append(gitblitUrl);
			sb.append(Constants.R_PATH);
			sb.append("{0}");
			final String cloneUrl = sb.toString();

			// list repositories
			final List<RepositoryModel> list = this.gitblit.getRepositoryModels(user);
			final Map<String, RepositoryModel> repositories = new HashMap<String, RepositoryModel>();
			for (final RepositoryModel model : list) {
				final String url = MessageFormat.format(cloneUrl, model.name);
				repositories.put(url, model);
			}
			result = repositories;
		} else if (RpcRequest.LIST_BRANCHES.equals(reqType)) {
			// list all local branches in all repositories accessible to user
			final Map<String, List<String>> localBranches = new HashMap<String, List<String>>();
			final List<RepositoryModel> models = this.gitblit.getRepositoryModels(user);
			for (final RepositoryModel model : models) {
				if (!model.hasCommits) {
					// skip empty repository
					continue;
				}
				if (model.isCollectingGarbage) {
					// skip garbage collecting repository
					this.logger.warn(MessageFormat.format(
							"Temporarily excluding {0} from RPC, busy collecting garbage",
							model.name));
					continue;
				}
				// get local branches
				final Repository repository = this.gitblit.getRepository(model.name);
				final List<RefModel> refs = JGitUtils.getLocalBranches(repository, false, -1);
				if (model.showRemoteBranches) {
					// add remote branches if repository displays them
					refs.addAll(JGitUtils.getRemoteBranches(repository, false, -1));
				}
				if (refs.size() > 0) {
					final List<String> branches = new ArrayList<String>();
					for (final RefModel ref : refs) {
						branches.add(ref.getName());
					}
					localBranches.put(model.name, branches);
				}
				repository.close();
			}
			result = localBranches;
		} else if (RpcRequest.GET_USER.equals(reqType)) {
			if (StringUtils.isEmpty(objectName)) {
				if (UserModel.ANONYMOUS.equals(user)) {
					response.sendError(this.forbiddenCode);
				} else {
					// return the current user, reset credentials
					final UserModel requestedUser = DeepCopier.copy(user);
					result = requestedUser;
				}
			} else {
				if (user.canAdmin() || objectName.equals(user.username)) {
					// return the specified user
					final UserModel requestedUser = this.gitblit.getUserModel(objectName);
					if (requestedUser == null) {
						response.setStatus(this.failureCode);
					} else {
						result = requestedUser;
					}
				} else {
					response.sendError(this.forbiddenCode);
				}
			}
		} else if (RpcRequest.LIST_USERS.equals(reqType)) {
			// list users
			final List<String> names = this.gitblit.getAllUsernames();
			final List<UserModel> users = new ArrayList<UserModel>();
			for (final String name : names) {
				users.add(this.gitblit.getUserModel(name));
			}
			result = users;
		} else if (RpcRequest.LIST_TEAMS.equals(reqType)) {
			// list teams
			final List<String> names = this.gitblit.getAllTeamNames();
			final List<TeamModel> teams = new ArrayList<TeamModel>();
			for (final String name : names) {
				teams.add(this.gitblit.getTeamModel(name));
			}
			result = teams;
		} else if (RpcRequest.CREATE_REPOSITORY.equals(reqType)) {
			// create repository
			final RepositoryModel model = deserialize(request, response, RepositoryModel.class);
			try {
				this.gitblit.updateRepositoryModel(model.name, model, true);
			}
			catch (final GitBlitException e) {
				response.setStatus(this.failureCode);
			}
		} else if (RpcRequest.FORK_REPOSITORY.equals(reqType)) {
			// fork repository
			final RepositoryModel origin = this.gitblit.getRepositoryModel(objectName);
			if (origin == null) {
				// failed to find repository, error is logged by the repository
				// manager
				response.setStatus(this.failureCode);
			} else {
				if ((user == null) || !user.canFork(origin)) {
					this.logger.error("User {} is not permitted to fork '{}'!",
							user == null ? "anonymous" : user.username, objectName);
					response.setStatus(this.failureCode);
				} else {
					try {
						// fork the origin
						final RepositoryModel fork = this.gitblit.fork(origin, user);
						if (fork == null) {
							this.logger.error("Failed to fork repository '{}'!", objectName);
							response.setStatus(this.failureCode);
						} else {
							this.logger.info("User {} has forked '{}'!", user.username, objectName);
						}
					}
					catch (final GitBlitException e) {
						response.setStatus(this.failureCode);
					}
				}
			}
		} else if (RpcRequest.EDIT_REPOSITORY.equals(reqType)) {
			// edit repository
			final RepositoryModel model = deserialize(request, response, RepositoryModel.class);
			// name specifies original repository name in event of rename
			String repoName = objectName;
			if (repoName == null) {
				repoName = model.name;
			}
			try {
				this.gitblit.updateRepositoryModel(repoName, model, false);
			}
			catch (final GitBlitException e) {
				response.setStatus(this.failureCode);
			}
		} else if (RpcRequest.DELETE_REPOSITORY.equals(reqType)) {
			// delete repository
			final RepositoryModel model = deserialize(request, response, RepositoryModel.class);
			this.gitblit.deleteRepositoryModel(model);
		} else if (RpcRequest.CREATE_USER.equals(reqType)) {
			// create user
			final UserModel model = deserialize(request, response, UserModel.class);
			try {
				this.gitblit.addUser(model);
			}
			catch (final GitBlitException e) {
				response.setStatus(this.failureCode);
			}
		} else if (RpcRequest.EDIT_USER.equals(reqType)) {
			// edit user
			final UserModel model = deserialize(request, response, UserModel.class);
			// name parameter specifies original user name in event of rename
			String username = objectName;
			if (username == null) {
				username = model.username;
			}
			try {
				this.gitblit.reviseUser(username, model);
			}
			catch (final GitBlitException e) {
				response.setStatus(this.failureCode);
			}
		} else if (RpcRequest.DELETE_USER.equals(reqType)) {
			// delete user
			final UserModel model = deserialize(request, response, UserModel.class);
			if (!this.gitblit.deleteUser(model.username)) {
				response.setStatus(this.failureCode);
			}
		} else if (RpcRequest.CREATE_TEAM.equals(reqType)) {
			// create team
			final TeamModel model = deserialize(request, response, TeamModel.class);
			try {
				this.gitblit.addTeam(model);
			}
			catch (final GitBlitException e) {
				response.setStatus(this.failureCode);
			}
		} else if (RpcRequest.EDIT_TEAM.equals(reqType)) {
			// edit team
			final TeamModel model = deserialize(request, response, TeamModel.class);
			// name parameter specifies original team name in event of rename
			String teamname = objectName;
			if (teamname == null) {
				teamname = model.name;
			}
			try {
				this.gitblit.reviseTeam(teamname, model);
			}
			catch (final GitBlitException e) {
				response.setStatus(this.failureCode);
			}
		} else if (RpcRequest.DELETE_TEAM.equals(reqType)) {
			// delete team
			final TeamModel model = deserialize(request, response, TeamModel.class);
			if (!this.gitblit.deleteTeam(model.name)) {
				response.setStatus(this.failureCode);
			}
		} else if (RpcRequest.LIST_REPOSITORY_MEMBERS.equals(reqType)) {
			// get repository members
			final RepositoryModel model = this.gitblit.getRepositoryModel(objectName);
			result = this.gitblit.getRepositoryUsers(model);
		} else if (RpcRequest.SET_REPOSITORY_MEMBERS.equals(reqType)) {
			// rejected since 1.2.0
			response.setStatus(this.failureCode);
		} else if (RpcRequest.LIST_REPOSITORY_MEMBER_PERMISSIONS.equals(reqType)) {
			// get repository member permissions
			final RepositoryModel model = this.gitblit.getRepositoryModel(objectName);
			result = this.gitblit.getUserAccessPermissions(model);
		} else if (RpcRequest.SET_REPOSITORY_MEMBER_PERMISSIONS.equals(reqType)) {
			// set the repository permissions for the specified users
			final RepositoryModel model = this.gitblit.getRepositoryModel(objectName);
			final Collection<RegistrantAccessPermission> permissions = deserialize(request,
					response, RpcUtils.REGISTRANT_PERMISSIONS_TYPE);
			result = this.gitblit.setUserAccessPermissions(model, permissions);
		} else if (RpcRequest.LIST_REPOSITORY_TEAMS.equals(reqType)) {
			// get repository teams
			final RepositoryModel model = this.gitblit.getRepositoryModel(objectName);
			result = this.gitblit.getRepositoryTeams(model);
		} else if (RpcRequest.SET_REPOSITORY_TEAMS.equals(reqType)) {
			// rejected since 1.2.0
			response.setStatus(this.failureCode);
		} else if (RpcRequest.LIST_REPOSITORY_TEAM_PERMISSIONS.equals(reqType)) {
			// get repository team permissions
			final RepositoryModel model = this.gitblit.getRepositoryModel(objectName);
			result = this.gitblit.getTeamAccessPermissions(model);
		} else if (RpcRequest.SET_REPOSITORY_TEAM_PERMISSIONS.equals(reqType)) {
			// set the repository permissions for the specified teams
			final RepositoryModel model = this.gitblit.getRepositoryModel(objectName);
			final Collection<RegistrantAccessPermission> permissions = deserialize(request,
					response, RpcUtils.REGISTRANT_PERMISSIONS_TYPE);
			result = this.gitblit.setTeamAccessPermissions(model, permissions);
		} else if (RpcRequest.LIST_FEDERATION_REGISTRATIONS.equals(reqType)) {
			// return the list of federation registrations
			if (allowAdmin) {
				result = this.gitblit.getFederationRegistrations();
			} else {
				response.sendError(this.notAllowedCode);
			}
		} else if (RpcRequest.LIST_FEDERATION_RESULTS.equals(reqType)) {
			// return the list of federation result registrations
			if (allowAdmin && this.gitblit.canFederate()) {
				result = this.gitblit.getFederationResultRegistrations();
			} else {
				response.sendError(this.notAllowedCode);
			}
		} else if (RpcRequest.LIST_FEDERATION_PROPOSALS.equals(reqType)) {
			// return the list of federation proposals
			if (allowAdmin && this.gitblit.canFederate()) {
				result = this.gitblit.getPendingFederationProposals();
			} else {
				response.sendError(this.notAllowedCode);
			}
		} else if (RpcRequest.LIST_FEDERATION_SETS.equals(reqType)) {
			// return the list of federation sets
			if (allowAdmin && this.gitblit.canFederate()) {
				String gitblitUrl = this.settings.getString(Keys.web.canonicalUrl, null);
				if (StringUtils.isEmpty(gitblitUrl)) {
					gitblitUrl = HttpUtils.getGitblitURL(request);
				}
				result = this.gitblit.getFederationSets(gitblitUrl);
			} else {
				response.sendError(this.notAllowedCode);
			}
		} else if (RpcRequest.LIST_SETTINGS.equals(reqType)) {
			// return the server's settings
			final ServerSettings serverSettings = this.gitblit.getSettingsModel();
			if (allowAdmin) {
				// return all settings
				result = serverSettings;
			} else {
				// anonymous users get a few settings to allow browser launching
				final List<String> keys = new ArrayList<String>();
				keys.add(Keys.web.siteName);
				keys.add(Keys.web.mountParameters);
				keys.add(Keys.web.syndicationEntries);

				if (allowManagement) {
					// keys necessary for repository and/or user management
					keys.add(Keys.realm.minPasswordLength);
					keys.add(Keys.realm.passwordStorage);
					keys.add(Keys.federation.sets);
				}
				// build the settings
				final ServerSettings managementSettings = new ServerSettings();
				for (final String key : keys) {
					managementSettings.add(serverSettings.get(key));
				}
				if (allowManagement) {
					managementSettings.pushScripts = serverSettings.pushScripts;
				}
				result = managementSettings;
			}
		} else if (RpcRequest.EDIT_SETTINGS.equals(reqType)) {
			// update settings on the server
			if (allowAdmin) {
				final Map<String, String> map = deserialize(request, response,
						RpcUtils.SETTINGS_TYPE);
				this.gitblit.updateSettings(map);
			} else {
				response.sendError(this.notAllowedCode);
			}
		} else if (RpcRequest.LIST_STATUS.equals(reqType)) {
			// return the server's status information
			if (allowAdmin) {
				result = this.gitblit.getStatus();
			} else {
				response.sendError(this.notAllowedCode);
			}
		} else if (RpcRequest.CLEAR_REPOSITORY_CACHE.equals(reqType)) {
			// clear the repository list cache
			if (allowManagement) {
				this.gitblit.resetRepositoryListCache();
			} else {
				response.sendError(this.notAllowedCode);
			}
		} else if (RpcRequest.REINDEX_TICKETS.equals(reqType)) {
			if (allowManagement) {
				if (StringUtils.isEmpty(objectName)) {
					// reindex all tickets
					this.gitblit.getTicketService().reindex();
				} else {
					// reindex tickets in a specific repository
					final RepositoryModel model = this.gitblit.getRepositoryModel(objectName);
					this.gitblit.getTicketService().reindex(model);
				}
			} else {
				response.sendError(this.notAllowedCode);
			}
		}

		// send the result of the request
		serialize(response, result);
	}
}
