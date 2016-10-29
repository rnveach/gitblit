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

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.models.ProjectModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.DeepCopier;
import com.gitblit.utils.ModelUtils;
import com.gitblit.utils.ObjectCache;
import com.gitblit.utils.StringUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Project manager handles project-related functions.
 *
 * @author James Moger
 *
 */
@Singleton
public class ProjectManager implements IProjectManager {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private Map<String, ProjectModel> projectCache = null;

	private final ObjectCache<String> projectMarkdownCache = new ObjectCache<String>();

	private final ObjectCache<String> projectRepositoriesMarkdownCache = new ObjectCache<String>();

	private final IStoredSettings settings;

	private final IRuntimeManager runtimeManager;

	private final IUserManager userManager;

	private final IRepositoryManager repositoryManager;

	private FileBasedConfig projectConfigs;

	@Inject
	public ProjectManager(IRuntimeManager runtimeManager, IUserManager userManager,
			IRepositoryManager repositoryManager) {

		this.settings = runtimeManager.getSettings();
		this.runtimeManager = runtimeManager;
		this.userManager = userManager;
		this.repositoryManager = repositoryManager;
	}

	@Override
	public ProjectManager start() {
		// load and cache the project metadata
		this.projectConfigs = new FileBasedConfig(this.runtimeManager.getFileOrFolder(
				Keys.web.projectsFile, "${baseFolder}/projects.conf"), FS.detect());
		getProjectConfigs();

		return this;
	}

	@Override
	public ProjectManager stop() {
		return this;
	}

	private void reloadProjectMarkdown(ProjectModel project) {
		// project markdown
		final File pmkd = new File(this.repositoryManager.getRepositoriesFolder(),
				(project.isRoot ? "" : project.name) + "/project.mkd");
		if (pmkd.exists()) {
			final Date lm = new Date(pmkd.lastModified());
			if (!this.projectMarkdownCache.hasCurrent(project.name, lm)) {
				final String mkd = com.gitblit.utils.FileUtils.readContent(pmkd, "\n");
				this.projectMarkdownCache.updateObject(project.name, lm, mkd);
			}
			project.projectMarkdown = this.projectMarkdownCache.getObject(project.name);
		}

		// project repositories markdown
		final File rmkd = new File(this.repositoryManager.getRepositoriesFolder(),
				(project.isRoot ? "" : project.name) + "/repositories.mkd");
		if (rmkd.exists()) {
			final Date lm = new Date(rmkd.lastModified());
			if (!this.projectRepositoriesMarkdownCache.hasCurrent(project.name, lm)) {
				final String mkd = com.gitblit.utils.FileUtils.readContent(rmkd, "\n");
				this.projectRepositoriesMarkdownCache.updateObject(project.name, lm, mkd);
			}
			project.repositoriesMarkdown = this.projectRepositoriesMarkdownCache
					.getObject(project.name);
		}
	}

	/**
	 * Returns the map of project config. This map is cached and reloaded if the
	 * underlying projects.conf file changes.
	 *
	 * @return project config map
	 */
	private Map<String, ProjectModel> getProjectConfigs() {
		if ((this.projectCache == null) || this.projectConfigs.isOutdated()) {

			try {
				this.projectConfigs.load();
			}
			catch (final Exception e) {
			}

			// project configs
			final String rootName = this.settings.getString(Keys.web.repositoryRootGroupName,
					"main");
			final ProjectModel rootProject = new ProjectModel(rootName, true);

			final Map<String, ProjectModel> configs = new HashMap<String, ProjectModel>();
			// cache the root project under its alias and an empty path
			configs.put("", rootProject);
			configs.put(rootProject.name.toLowerCase(), rootProject);

			for (final String name : this.projectConfigs.getSubsections("project")) {
				ProjectModel project;
				if (name.equalsIgnoreCase(rootName)) {
					project = rootProject;
				} else {
					project = new ProjectModel(name);
				}
				project.title = this.projectConfigs.getString("project", name, "title");
				project.description = this.projectConfigs.getString("project", name, "description");

				reloadProjectMarkdown(project);

				configs.put(name.toLowerCase(), project);
			}
			this.projectCache = new ConcurrentHashMap<String, ProjectModel>();
			this.projectCache.putAll(configs);
		}
		return this.projectCache;
	}

	/**
	 * Returns a list of project models for the user.
	 *
	 * @param user
	 * @param includeUsers
	 * @return list of projects that are accessible to the user
	 */
	@Override
	public List<ProjectModel> getProjectModels(UserModel user, boolean includeUsers) {
		final Map<String, ProjectModel> configs = getProjectConfigs();

		// per-user project lists, this accounts for security and visibility
		final Map<String, ProjectModel> map = new TreeMap<String, ProjectModel>();
		// root project
		map.put("", configs.get(""));

		for (final RepositoryModel model : this.repositoryManager.getRepositoryModels(user)) {
			final String projectPath = StringUtils.getRootPath(model.name);
			final String projectKey = projectPath.toLowerCase();
			if (!map.containsKey(projectKey)) {
				ProjectModel project;
				if (configs.containsKey(projectKey)) {
					// clone the project model because it's repository list will
					// be tailored for the requesting user
					project = DeepCopier.copy(configs.get(projectKey));
				} else {
					project = new ProjectModel(projectPath);
				}
				map.put(projectKey, project);
			}
			map.get(projectKey).addRepository(model);
		}

		// sort projects, root project first
		List<ProjectModel> projects;
		if (includeUsers) {
			// all projects
			projects = new ArrayList<ProjectModel>(map.values());
			Collections.sort(projects);
			projects.remove(map.get(""));
			projects.add(0, map.get(""));
		} else {
			// all non-user projects
			projects = new ArrayList<ProjectModel>();
			final ProjectModel root = map.remove("");
			for (final ProjectModel model : map.values()) {
				if (!model.isUserProject()) {
					projects.add(model);
				}
			}
			Collections.sort(projects);
			projects.add(0, root);
		}
		return projects;
	}

	/**
	 * Returns the project model for the specified user.
	 *
	 * @param name
	 * @param user
	 * @return a project model, or null if it does not exist
	 */
	@Override
	public ProjectModel getProjectModel(String name, UserModel user) {
		for (final ProjectModel project : getProjectModels(user, true)) {
			if (project.name.equalsIgnoreCase(name)) {
				return project;
			}
		}
		return null;
	}

	/**
	 * Returns a project model for the Gitblit/system user.
	 *
	 * @param name
	 *            a project name
	 * @return a project model or null if the project does not exist
	 */
	@Override
	public ProjectModel getProjectModel(String name) {
		final Map<String, ProjectModel> configs = getProjectConfigs();
		ProjectModel project = configs.get(name.toLowerCase());
		if (project == null) {
			project = new ProjectModel(name);
			if (ModelUtils.isPersonalRepository(name)) {
				final UserModel user = this.userManager
						.getUserModel(ModelUtils.getUserNameFromRepoPath(name));
				if (user != null) {
					project.title = user.getDisplayName();
					project.description = "personal repositories";
				}
			}
		} else {
			// clone the object
			project = DeepCopier.copy(project);
		}
		if (StringUtils.isEmpty(name)) {
			// get root repositories
			for (final String repository : this.repositoryManager.getRepositoryList()) {
				if (repository.indexOf('/') == -1) {
					project.addRepository(repository);
				}
			}
		} else {
			// get repositories in subfolder
			final String folder = name.toLowerCase() + "/";
			for (final String repository : this.repositoryManager.getRepositoryList()) {
				if (repository.toLowerCase().startsWith(folder)) {
					project.addRepository(repository);
				}
			}
		}
		if (project.repositories.size() == 0) {
			// no repositories == no project
			return null;
		}

		reloadProjectMarkdown(project);
		return project;
	}

	/**
	 * Returns the list of project models that are referenced by the supplied
	 * repository model list. This is an alternative method exists to ensure
	 * Gitblit does not call getRepositoryModels(UserModel) twice in a request.
	 *
	 * @param repositoryModels
	 * @param includeUsers
	 * @return a list of project models
	 */
	@Override
	public List<ProjectModel> getProjectModels(List<RepositoryModel> repositoryModels,
			boolean includeUsers) {
		final Map<String, ProjectModel> projects = new LinkedHashMap<String, ProjectModel>();
		for (final RepositoryModel repository : repositoryModels) {
			if (!includeUsers && repository.isPersonalRepository()) {
				// exclude personal repositories
				continue;
			}
			if (!projects.containsKey(repository.projectPath)) {
				final ProjectModel project = getProjectModel(repository.projectPath);
				if (project == null) {
					this.logger.warn(MessageFormat.format(
							"excluding project \"{0}\" from project list because it is empty!",
							repository.projectPath));
					continue;
				}
				projects.put(repository.projectPath, project);
				// clear the repo list in the project because that is the system
				// list, not the user-accessible list and start building the
				// user-accessible list
				project.repositories.clear();
				project.repositories.add(repository.name);
				project.lastChange = repository.lastChange;
			} else {
				// update the user-accessible list
				// this is used for repository count
				final ProjectModel project = projects.get(repository.projectPath);
				project.repositories.add(repository.name);
				if (project.lastChange.before(repository.lastChange)) {
					project.lastChange = repository.lastChange;
				}
			}
		}
		return new ArrayList<ProjectModel>(projects.values());
	}
}
