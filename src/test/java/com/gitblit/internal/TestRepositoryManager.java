package com.gitblit.internal;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.Repository;

import com.gitblit.GitBlitException;
import com.gitblit.manager.IManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.models.ForkModel;
import com.gitblit.models.Metric;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.SearchResult;
import com.gitblit.models.UserModel;

public final class TestRepositoryManager implements IRepositoryManager {
	private static final Map<String, Repository> repositoryList = new HashMap<>();

	private static TestRepositoryManager instance = new TestRepositoryManager();

	private TestRepositoryManager() {
	}

	public static TestRepositoryManager getInstance() {
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
	public File getRepositoriesFolder() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public File getHooksFolder() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public File getGrapesFolder() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getLastActivityDate() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<RegistrantAccessPermission> getUserAccessPermissions(
			UserModel user) {
		// TODO Auto-generated method stub
		return Collections.emptyList();
	}

	@Override
	public List<RegistrantAccessPermission> getUserAccessPermissions(
			RepositoryModel repository) {
		// TODO Auto-generated method stub
		return Collections.emptyList();
	}

	@Override
	public boolean setUserAccessPermissions(RepositoryModel repository,
			Collection<RegistrantAccessPermission> permissions) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public List<String> getRepositoryUsers(RepositoryModel repository) {
		// TODO Auto-generated method stub
		return Collections.emptyList();
	}

	@Override
	public List<RegistrantAccessPermission> getTeamAccessPermissions(
			RepositoryModel repository) {
		// TODO Auto-generated method stub
		return Collections.emptyList();
	}

	@Override
	public boolean setTeamAccessPermissions(RepositoryModel repository,
			Collection<RegistrantAccessPermission> permissions) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public List<String> getRepositoryTeams(RepositoryModel repository) {
		// TODO Auto-generated method stub
		return Collections.emptyList();
	}

	@Override
	public void addToCachedRepositoryList(RepositoryModel model) {
		// TODO Auto-generated method stub

	}

	@Override
	public void resetRepositoryListCache() {
		// TODO Auto-generated method stub

	}

	@Override
	public void resetRepositoryCache(String repositoryName) {
		// TODO Auto-generated method stub

	}

	@Override
	public List<String> getRepositoryList() {
		return new ArrayList<>(repositoryList.keySet());
	}

	@Override
	public Repository getRepository(String repositoryName) {
		return repositoryList.get(repositoryName);
	}

	@Override
	public Repository getRepository(String repositoryName, boolean logError) {
		return getRepository(repositoryName);
	}

	@Override
	public List<RepositoryModel> getRepositoryModels() {
		// TODO Auto-generated method stub
		return Collections.emptyList();
	}

	@Override
	public List<RepositoryModel> getRepositoryModels(UserModel user) {
		// TODO Auto-generated method stub
		return Collections.emptyList();
	}

	@Override
	public RepositoryModel getRepositoryModel(UserModel user,
			String repositoryName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public RepositoryModel getRepositoryModel(String repositoryName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getStarCount(RepositoryModel repository) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean hasRepository(String repositoryName) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean hasRepository(String repositoryName,
			boolean caseSensitiveCheck) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean hasFork(String username, String origin) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getFork(String username, String origin) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ForkModel getForkNetwork(String repository) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long updateLastChangeFields(Repository r, RepositoryModel model) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public List<Metric> getRepositoryDefaultMetrics(RepositoryModel model,
			Repository repository) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void updateRepositoryModel(String repositoryName,
			RepositoryModel repository, boolean isCreate)
			throws GitBlitException {
		// TODO Auto-generated method stub

	}

	@Override
	public void updateConfiguration(Repository r, RepositoryModel repository) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean canDelete(RepositoryModel model) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean deleteRepositoryModel(RepositoryModel model) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean deleteRepository(String repositoryName) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public List<String> getAllScripts() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getPreReceiveScriptsInherited(RepositoryModel repository) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getPreReceiveScriptsUnused(RepositoryModel repository) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getPostReceiveScriptsInherited(
			RepositoryModel repository) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getPostReceiveScriptsUnused(RepositoryModel repository) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<SearchResult> search(String query, int page, int pageSize,
			List<String> repositories) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isCollectingGarbage() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCollectingGarbage(String repositoryName) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void closeAll() {
		// TODO Auto-generated method stub

	}

	@Override
	public void close(String repository) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isIdle(Repository repository) {
		// TODO Auto-generated method stub
		return false;
	}

	// ////////////////////////////////////////////////////////////////////////

	public static void addRepository(String name) throws IOException {
		addRepository(name, GitUtils.createNewRepository());
	}

	public static void addRepository(String name, Repository repository) {
		repositoryList.put(name, repository);
	}
}
