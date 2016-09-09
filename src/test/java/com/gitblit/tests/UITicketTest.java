/*
 * Copyright 2014 gitblit.com.
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

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IPluginManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.manager.NotificationManager;
import com.gitblit.manager.PluginManager;
import com.gitblit.manager.RepositoryManager;
import com.gitblit.manager.RuntimeManager;
import com.gitblit.manager.UserManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.Field;
import com.gitblit.models.TicketModel.Priority;
import com.gitblit.models.TicketModel.Severity;
import com.gitblit.models.TicketModel.Type;
import com.gitblit.tests.mock.MemorySettings;
import com.gitblit.tickets.BranchTicketService;
import com.gitblit.tickets.ITicketService;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.XssFilter;
import com.gitblit.utils.XssFilter.AllowXssFilter;

/**
 * Generates the range of tickets to ease testing of the look and feel of
 * tickets
 */
public class UITicketTest extends GitblitUnitTest {

	private ITicketService service;
	final String repoName = "UITicketTest.git";
	final RepositoryModel repo = new RepositoryModel(this.repoName, null, null, null);

	protected ITicketService getService(boolean deleteAll) throws Exception {

		final IStoredSettings settings = getSettings(deleteAll);
		final XssFilter xssFilter = new AllowXssFilter();
		final IRuntimeManager runtimeManager = new RuntimeManager(settings, xssFilter).start();
		final IPluginManager pluginManager = new PluginManager(runtimeManager).start();
		final INotificationManager notificationManager = new NotificationManager(settings).start();
		final IUserManager userManager = new UserManager(runtimeManager, pluginManager).start();
		final IRepositoryManager repositoryManager = new RepositoryManager(runtimeManager,
				pluginManager, userManager).start();

		final BranchTicketService service = new BranchTicketService(runtimeManager, pluginManager,
				notificationManager, userManager, repositoryManager).start();

		if (deleteAll) {
			service.deleteAll(this.repo);
		}
		return service;
	}

	protected IStoredSettings getSettings(boolean deleteAll) throws Exception {
		final File dir = new File(GitBlitSuite.REPOSITORIES, this.repoName);
		if (deleteAll) {
			FileUtils.deleteDirectory(dir);
			JGitUtils.createRepository(GitBlitSuite.REPOSITORIES, this.repoName).close();
		}

		final File luceneDir = new File(dir, "tickets/lucene");
		luceneDir.mkdirs();

		final Map<String, Object> map = new HashMap<String, Object>();
		map.put(Keys.git.repositoriesFolder, GitBlitSuite.REPOSITORIES.getAbsolutePath());
		map.put(Keys.tickets.indexFolder, luceneDir.getAbsolutePath());

		final IStoredSettings settings = new MemorySettings(map);
		return settings;
	}

	@Before
	public void setup() throws Exception {
		this.service = getService(true);
	}

	@After
	public void cleanup() {
		this.service.stop();
	}

	@Test
	public void UITicketOptions() {

		for (final TicketModel.Type t : TicketModel.Type.values()) {
			for (final TicketModel.Priority p : TicketModel.Priority.values()) {
				for (final TicketModel.Severity s : TicketModel.Severity.values()) {
					assertNotNull(this.service.createTicket(this.repo, newChange(t, p, s)));
				}
			}
		}
	}

	private static Change newChange(Type type, Priority priority, Severity severity) {
		final Change change = new Change("JUnit");
		change.setField(Field.title,
				String.format("Type: %s | Priority: %s | Severity: %s", type, priority, severity));
		change.setField(Field.type, type);
		change.setField(Field.severity, severity);
		change.setField(Field.priority, priority);
		return change;
	}

}