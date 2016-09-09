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
package com.gitblit.tests;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.Constants.PermissionType;
import com.gitblit.Constants.RegistrantType;
import com.gitblit.GitBlitException.ForbiddenException;
import com.gitblit.GitBlitException.UnauthorizedException;
import com.gitblit.Keys;
import com.gitblit.models.FederationModel;
import com.gitblit.models.FederationProposal;
import com.gitblit.models.FederationSet;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.ServerStatus;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.servlet.RpcServlet;
import com.gitblit.utils.RpcUtils;

/**
 * Tests all the rpc client utility methods, the rpc filter and rpc servlet.
 *
 * @author James Moger
 *
 */
public class RpcTests extends GitblitUnitTest {

	String url = GitBlitSuite.url;
	String account = GitBlitSuite.account;
	String password = GitBlitSuite.password;

	private static final AtomicBoolean started = new AtomicBoolean(false);

	@BeforeClass
	public static void startGitblit() throws Exception {
		started.set(GitBlitSuite.startGitblit());
	}

	@AfterClass
	public static void stopGitblit() throws Exception {
		if (started.get()) {
			GitBlitSuite.stopGitblit();
		}
	}

	@Test
	public void testGetProtocolVersion() throws IOException {
		final int protocol = RpcUtils.getProtocolVersion(this.url, null, null);
		assertEquals(RpcServlet.PROTOCOL_VERSION, protocol);
	}

	@Test
	public void testListRepositories() throws IOException {
		final Map<String, RepositoryModel> map = RpcUtils.getRepositories(this.url, null, null);
		assertNotNull("Repository list is null!", map);
		assertTrue("Repository list is empty!", map.size() > 0);
	}

	@Test
	public void testListUsers() throws IOException {
		List<UserModel> list = null;
		try {
			list = RpcUtils.getUsers(this.url, null, null);
		}
		catch (final UnauthorizedException e) {
		}
		assertNull("Server allows anyone to admin!", list);

		list = RpcUtils.getUsers(this.url, "admin", "admin".toCharArray());
		assertTrue("User list is empty!", list.size() > 0);
	}

	@Test
	public void testGetUser() throws IOException {
		UserModel user = null;
		try {
			user = RpcUtils.getUser("admin", this.url, null, null);
		}
		catch (final ForbiddenException e) {
		}
		assertNull("Server allows anyone to get user!", user);

		user = RpcUtils.getUser("admin", this.url, "admin", "admin".toCharArray());
		assertEquals("User is not the admin!", "admin", user.username);
		assertTrue("User is not an administrator!", user.canAdmin());
	}

	@Test
	public void testListTeams() throws IOException {
		List<TeamModel> list = null;
		try {
			list = RpcUtils.getTeams(this.url, null, null);
		}
		catch (final UnauthorizedException e) {
		}
		assertNull("Server allows anyone to admin!", list);

		list = RpcUtils.getTeams(this.url, "admin", "admin".toCharArray());
		assertTrue("Team list is empty!", list.size() > 0);
		assertEquals("admins", list.get(0).name);
	}

	@Test
	public void testUserAdministration() throws IOException {
		final UserModel user = new UserModel("garbage");
		user.canAdmin = true;
		user.password = "whocares";

		// create
		assertTrue("Failed to create user!",
				RpcUtils.createUser(user, this.url, this.account, this.password.toCharArray()));

		UserModel retrievedUser = findUser(user.username);
		assertNotNull("Failed to find " + user.username, retrievedUser);
		assertTrue("Retrieved user can not administer Gitblit", retrievedUser.canAdmin);

		// rename and toggle admin permission
		final String originalName = user.username;
		user.username = "garbage2";
		user.canAdmin = false;
		assertTrue(
				"Failed to update user!",
				RpcUtils.updateUser(originalName, user, this.url, this.account,
						this.password.toCharArray()));

		retrievedUser = findUser(user.username);
		assertNotNull("Failed to find " + user.username, retrievedUser);
		assertTrue("Retrieved user did not update", !retrievedUser.canAdmin);

		// delete
		assertTrue(
				"Failed to delete " + user.username,
				RpcUtils.deleteUser(retrievedUser, this.url, this.account,
						this.password.toCharArray()));

		retrievedUser = findUser(user.username);
		assertNull("Failed to delete " + user.username, retrievedUser);
	}

	private UserModel findUser(String name) throws IOException {
		final List<UserModel> users = RpcUtils.getUsers(this.url, this.account,
				this.password.toCharArray());
		UserModel retrievedUser = null;
		for (final UserModel model : users) {
			if (model.username.equalsIgnoreCase(name)) {
				retrievedUser = model;
				break;
			}
		}
		return retrievedUser;
	}

	@Test
	public void testRepositoryAdministration() throws IOException {
		final RepositoryModel model = new RepositoryModel();
		model.name = "garbagerepo.git";
		model.description = "created by RpcUtils";
		model.addOwner("garbage");
		model.accessRestriction = AccessRestrictionType.VIEW;
		model.authorizationControl = AuthorizationControl.AUTHENTICATED;

		// create
		RpcUtils.deleteRepository(model, this.url, this.account, this.password.toCharArray());
		assertTrue(
				"Failed to create repository!",
				RpcUtils.createRepository(model, this.url, this.account,
						this.password.toCharArray()));

		RepositoryModel retrievedRepository = findRepository(model.name);
		assertNotNull("Failed to find " + model.name, retrievedRepository);
		assertEquals(AccessRestrictionType.VIEW, retrievedRepository.accessRestriction);
		assertEquals(AuthorizationControl.AUTHENTICATED, retrievedRepository.authorizationControl);

		// rename and change access restriciton
		final String originalName = model.name;
		model.name = "garbagerepo2.git";
		model.accessRestriction = AccessRestrictionType.CLONE;
		model.authorizationControl = AuthorizationControl.NAMED;
		RpcUtils.deleteRepository(model, this.url, this.account, this.password.toCharArray());
		assertTrue("Failed to update repository!", RpcUtils.updateRepository(originalName, model,
				this.url, this.account, this.password.toCharArray()));

		retrievedRepository = findRepository(model.name);
		assertNotNull("Failed to find " + model.name, retrievedRepository);
		assertTrue("Access retriction type is wrong",
				AccessRestrictionType.CLONE.equals(retrievedRepository.accessRestriction));

		// restore VIEW restriction
		retrievedRepository.accessRestriction = AccessRestrictionType.VIEW;
		assertTrue("Failed to update repository!", RpcUtils.updateRepository(
				retrievedRepository.name, retrievedRepository, this.url, this.account,
				this.password.toCharArray()));
		retrievedRepository = findRepository(retrievedRepository.name);

		// memberships
		final UserModel testMember = new UserModel("justadded");
		assertTrue(RpcUtils.createUser(testMember, this.url, this.account,
				this.password.toCharArray()));

		List<RegistrantAccessPermission> permissions = RpcUtils.getRepositoryMemberPermissions(
				retrievedRepository, this.url, this.account, this.password.toCharArray());
		assertEquals("Unexpected permissions! " + permissions.toString(), 1, permissions.size());
		permissions.add(new RegistrantAccessPermission(testMember.username, AccessPermission.VIEW,
				PermissionType.EXPLICIT, RegistrantType.USER, null, true));
		assertTrue("Failed to set member permissions!", RpcUtils.setRepositoryMemberPermissions(
				retrievedRepository, permissions, this.url, this.account,
				this.password.toCharArray()));
		permissions = RpcUtils.getRepositoryMemberPermissions(retrievedRepository, this.url,
				this.account, this.password.toCharArray());
		boolean foundMember = false;
		for (final RegistrantAccessPermission permission : permissions) {
			if (permission.registrant.equalsIgnoreCase(testMember.username)) {
				foundMember = true;
				assertEquals(AccessPermission.VIEW, permission.permission);
				break;
			}
		}
		assertTrue("Failed to find member!", foundMember);

		// delete
		assertTrue("Failed to delete " + model.name, RpcUtils.deleteRepository(retrievedRepository,
				this.url, this.account, this.password.toCharArray()));

		retrievedRepository = findRepository(model.name);
		assertNull("Failed to delete " + model.name, retrievedRepository);

		for (final UserModel u : RpcUtils.getUsers(this.url, this.account,
				this.password.toCharArray())) {
			if (u.username.equals(testMember.username)) {
				assertTrue(RpcUtils.deleteUser(u, this.url, this.account,
						this.password.toCharArray()));
				break;
			}
		}
	}

	private RepositoryModel findRepository(String name) throws IOException {
		final Map<String, RepositoryModel> repositories = RpcUtils.getRepositories(this.url,
				this.account, this.password.toCharArray());
		RepositoryModel retrievedRepository = null;
		for (final RepositoryModel model : repositories.values()) {
			if (model.name.equalsIgnoreCase(name)) {
				retrievedRepository = model;
				break;
			}
		}
		return retrievedRepository;
	}

	@Test
	public void testTeamAdministration() throws IOException {
		List<TeamModel> teams = RpcUtils.getTeams(this.url, this.account,
				this.password.toCharArray());
		assertEquals(1, teams.size());

		// Create the A-Team
		TeamModel aTeam = new TeamModel("A-Team");
		aTeam.users.add("admin");
		aTeam.addRepositoryPermission("helloworld.git");
		assertTrue(RpcUtils.createTeam(aTeam, this.url, this.account, this.password.toCharArray()));

		aTeam = null;
		teams = RpcUtils.getTeams(this.url, this.account, this.password.toCharArray());
		assertEquals(2, teams.size());
		for (final TeamModel team : teams) {
			if (team.name.equals("A-Team")) {
				aTeam = team;
				break;
			}
		}
		assertNotNull(aTeam);
		assertTrue(aTeam.hasUser("admin"));
		assertTrue(aTeam.hasRepositoryPermission("helloworld.git"));

		RepositoryModel helloworld = null;
		final Map<String, RepositoryModel> repositories = RpcUtils.getRepositories(this.url,
				this.account, this.password.toCharArray());
		for (final RepositoryModel repository : repositories.values()) {
			if (repository.name.equals("helloworld.git")) {
				helloworld = repository;
				break;
			}
		}
		assertNotNull(helloworld);

		// Confirm that we have added the team
		List<String> helloworldTeams = RpcUtils.getRepositoryTeams(helloworld, this.url,
				this.account, this.password.toCharArray());
		assertEquals(1, helloworldTeams.size());
		assertTrue(helloworldTeams.contains(aTeam.name));

		// set no teams
		final List<RegistrantAccessPermission> permissions = new ArrayList<RegistrantAccessPermission>();
		for (final String team : helloworldTeams) {
			permissions.add(new RegistrantAccessPermission(team, AccessPermission.NONE,
					PermissionType.EXPLICIT, RegistrantType.TEAM, null, true));
		}
		assertTrue(RpcUtils.setRepositoryTeamPermissions(helloworld, permissions, this.url,
				this.account, this.password.toCharArray()));
		helloworldTeams = RpcUtils.getRepositoryTeams(helloworld, this.url, this.account,
				this.password.toCharArray());
		assertEquals(0, helloworldTeams.size());

		// delete the A-Team
		assertTrue(RpcUtils.deleteTeam(aTeam, this.url, this.account, this.password.toCharArray()));

		teams = RpcUtils.getTeams(this.url, this.account, this.password.toCharArray());
		assertEquals(1, teams.size());
	}

	@Test
	public void testFederationRegistrations() throws Exception {
		final List<FederationModel> registrations = RpcUtils.getFederationRegistrations(this.url,
				this.account, this.password.toCharArray());
		assertTrue("No federation registrations were retrieved!", registrations.size() >= 0);
	}

	@Test
	public void testFederationResultRegistrations() throws Exception {
		final List<FederationModel> registrations = RpcUtils.getFederationResultRegistrations(
				this.url, this.account, this.password.toCharArray());
		assertTrue("No federation result registrations were retrieved!", registrations.size() >= 0);
	}

	@Test
	public void testFederationProposals() throws Exception {
		final List<FederationProposal> proposals = RpcUtils.getFederationProposals(this.url,
				this.account, this.password.toCharArray());
		assertTrue("No federation proposals were retrieved!", proposals.size() >= 0);
	}

	@Test
	public void testFederationSets() throws Exception {
		final List<FederationSet> sets = RpcUtils.getFederationSets(this.url, this.account,
				this.password.toCharArray());
		assertTrue("No federation sets were retrieved!", sets.size() >= 0);
	}

	@Test
	public void testSettings() throws Exception {
		final ServerSettings settings = RpcUtils.getSettings(this.url, this.account,
				this.password.toCharArray());
		assertNotNull("No settings were retrieved!", settings);
	}

	@Test
	public void testServerStatus() throws Exception {
		final ServerStatus status = RpcUtils.getStatus(this.url, this.account,
				this.password.toCharArray());
		assertNotNull("No status was retrieved!", status);
	}

	@Test
	public void testUpdateSettings() throws Exception {
		final Map<String, String> updated = new HashMap<String, String>();

		// grab current setting
		ServerSettings settings = RpcUtils.getSettings(this.url, this.account,
				this.password.toCharArray());
		boolean showSizes = settings.get(Keys.web.showRepositorySizes).getBoolean(true);
		showSizes = !showSizes;

		// update setting
		updated.put(Keys.web.showRepositorySizes, String.valueOf(showSizes));
		boolean success = RpcUtils.updateSettings(updated, this.url, this.account,
				this.password.toCharArray());
		assertTrue("Failed to update server settings", success);

		// confirm setting change
		settings = RpcUtils.getSettings(this.url, this.account, this.password.toCharArray());
		boolean newValue = settings.get(Keys.web.showRepositorySizes).getBoolean(false);
		assertEquals(newValue, showSizes);

		// restore setting
		newValue = !newValue;
		updated.put(Keys.web.showRepositorySizes, String.valueOf(newValue));
		success = RpcUtils.updateSettings(updated, this.url, this.account,
				this.password.toCharArray());
		assertTrue("Failed to update server settings", success);
		settings = RpcUtils.getSettings(this.url, this.account, this.password.toCharArray());
		showSizes = settings.get(Keys.web.showRepositorySizes).getBoolean(true);
		assertEquals(newValue, showSizes);
	}

	@Test
	public void testBranches() throws Exception {
		final Map<String, Collection<String>> branches = RpcUtils.getBranches(this.url,
				this.account, this.password.toCharArray());
		assertNotNull(branches);
		assertTrue(branches.size() > 0);
	}

	@Test
	public void testFork() throws Exception {
		// test forking by an administrator
		// admins are all-powerful and can fork the unforakable :)
		testFork(this.account, this.password, true, true);
		testFork(this.account, this.password, false, true);

		// test forking by a permitted normal user
		final UserModel forkUser = new UserModel("forkuser");
		forkUser.password = forkUser.username;
		forkUser.canFork = true;
		RpcUtils.deleteUser(forkUser, this.url, this.account, this.password.toCharArray());
		RpcUtils.createUser(forkUser, this.url, this.account, this.password.toCharArray());
		testFork(forkUser.username, forkUser.password, true, true);
		testFork(forkUser.username, forkUser.password, false, false);
		RpcUtils.deleteUser(forkUser, this.url, this.account, this.password.toCharArray());

		// test forking by a non-permitted normal user
		final UserModel noForkUser = new UserModel("noforkuser");
		noForkUser.password = noForkUser.username;
		noForkUser.canFork = false;
		RpcUtils.deleteUser(noForkUser, this.url, this.account, this.password.toCharArray());
		RpcUtils.createUser(noForkUser, this.url, this.account, this.password.toCharArray());
		testFork(forkUser.username, forkUser.password, true, false);
		testFork(forkUser.username, forkUser.password, false, false);
		RpcUtils.deleteUser(noForkUser, this.url, this.account, this.password.toCharArray());
	}

	private void testFork(String forkAcct, String forkAcctPassword, boolean allowForks,
			boolean expectSuccess) throws Exception {
		// test does not exist
		final RepositoryModel dne = new RepositoryModel();
		dne.name = "doesNotExist.git";
		assertFalse(String.format("Successfully forked %s!", dne.name),
				RpcUtils.forkRepository(dne, this.url, forkAcct, forkAcctPassword.toCharArray()));

		// delete any previous fork
		final RepositoryModel fork = findRepository(String.format("~%s/helloworld.git", forkAcct));
		if (fork != null) {
			RpcUtils.deleteRepository(fork, this.url, this.account, this.password.toCharArray());
		}

		// update the origin to allow forks or not
		final RepositoryModel origin = findRepository("helloworld.git");
		origin.allowForks = allowForks;
		RpcUtils.updateRepository(origin.name, origin, this.url, this.account,
				this.password.toCharArray());

		// fork the repository
		if (expectSuccess) {
			assertTrue(
					String.format("Failed to fork %s!", origin.name),
					RpcUtils.forkRepository(origin, this.url, forkAcct,
							forkAcctPassword.toCharArray()));
		} else {
			assertFalse(
					String.format("Successfully forked %s!", origin.name),
					RpcUtils.forkRepository(origin, this.url, forkAcct,
							forkAcctPassword.toCharArray()));
		}

		// attempt another fork
		assertFalse(String.format("Successfully forked %s!", origin.name),
				RpcUtils.forkRepository(origin, this.url, forkAcct, forkAcctPassword.toCharArray()));

		// delete the fork repository
		RpcUtils.deleteRepository(fork, this.url, this.account, this.password.toCharArray());
	}
}
