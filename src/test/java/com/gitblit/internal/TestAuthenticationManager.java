package com.gitblit.internal;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.gitblit.Constants.Role;
import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.manager.IManager;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.SshKey;

public final class TestAuthenticationManager implements IAuthenticationManager {
	private static UserModel userModel;

	private static TestAuthenticationManager instance = new TestAuthenticationManager();

	private TestAuthenticationManager() {
	}

	public static TestAuthenticationManager getInstance() {
		return instance;
	}

	@Override
	public IManager start() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IManager stop() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public UserModel authenticate(HttpServletRequest httpRequest) {
		return userModel;
	}

	@Override
	public UserModel authenticate(String username, SshKey key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public UserModel authenticate(HttpServletRequest httpRequest,
			boolean requiresCertificate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public UserModel authenticate(String username, char[] password,
			String remoteIP) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public UserModel authenticate(String username) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getCookie(HttpServletRequest request) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setCookie(HttpServletResponse response, UserModel user) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setCookie(HttpServletRequest request,
			HttpServletResponse response, UserModel user) {
		// TODO Auto-generated method stub

	}

	@Override
	public void logout(HttpServletResponse response, UserModel user) {
		// TODO Auto-generated method stub

	}

	@Override
	public void logout(HttpServletRequest request,
			HttpServletResponse response, UserModel user) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean supportsCredentialChanges(UserModel user) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsDisplayNameChanges(UserModel user) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsEmailAddressChanges(UserModel user) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsTeamMembershipChanges(UserModel user) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsTeamMembershipChanges(TeamModel team) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsRoleChanges(UserModel user, Role role) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsRoleChanges(TeamModel team, Role role) {
		// TODO Auto-generated method stub
		return false;
	}

	// ////////////////////////////////////////////////////////////////////////

	public static void setUserModel(UserModel userModel) {
		TestAuthenticationManager.userModel = userModel;
	}

	public static void reset() {
		userModel = UserModel.ANONYMOUS;
	}
}
