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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Insets;
import java.io.IOException;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.gitblit.client.ClosableTabComponent.CloseTabListener;
import com.gitblit.models.FeedModel;

/**
 * GitblitPanel is a container for the repository, users, settings, etc panels.
 *
 * @author James Moger
 *
 */
public class GitblitPanel extends JPanel implements CloseTabListener {

	private static final long serialVersionUID = 1L;

	private final RegistrationsDialog.RegistrationListener listener;

	private GitblitClient gitblit;

	private final JTabbedPane tabs;

	private RepositoriesPanel repositoriesPanel;

	private FeedsPanel feedsPanel;

	private UsersPanel usersPanel;

	private TeamsPanel teamsPanel;

	private SettingsPanel settingsPanel;

	private StatusPanel statusPanel;

	public GitblitPanel(GitblitRegistration reg, RegistrationsDialog.RegistrationListener listener) {
		this.gitblit = new GitblitClient(reg);
		this.listener = listener;

		this.tabs = new JTabbedPane(SwingConstants.BOTTOM);
		this.tabs.addTab(Translation.get("gb.repositories"), createRepositoriesPanel());
		this.tabs.addTab(Translation.get("gb.activity"), createFeedsPanel());
		this.tabs.addTab(Translation.get("gb.teams"), createTeamsPanel());
		this.tabs.addTab(Translation.get("gb.users"), createUsersPanel());
		this.tabs.addTab(Translation.get("gb.settings"), createSettingsPanel());
		this.tabs.addTab(Translation.get("gb.status"), createStatusPanel());
		this.tabs.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				GitblitPanel.this.tabs.getSelectedComponent().requestFocus();
			}
		});

		setLayout(new BorderLayout());
		add(this.tabs, BorderLayout.CENTER);
	}

	private JPanel createRepositoriesPanel() {
		this.repositoriesPanel = new RepositoriesPanel(this.gitblit) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void subscribeFeeds(List<FeedModel> feeds) {
				GitblitPanel.this.subscribeFeeds(feeds);
			}

			@Override
			protected void updateUsersTable() {
				GitblitPanel.this.usersPanel.updateTable(false);
			}

			@Override
			protected void updateTeamsTable() {
				GitblitPanel.this.teamsPanel.updateTable(false);
			}

		};
		return this.repositoriesPanel;
	}

	private JPanel createFeedsPanel() {
		this.feedsPanel = new FeedsPanel(this.gitblit) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void subscribeFeeds(List<FeedModel> feeds) {
				GitblitPanel.this.subscribeFeeds(feeds);
			}
		};
		return this.feedsPanel;
	}

	private JPanel createUsersPanel() {
		this.usersPanel = new UsersPanel(this.gitblit) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void updateTeamsTable() {
				GitblitPanel.this.teamsPanel.updateTable(false);
			}
		};
		return this.usersPanel;
	}

	private JPanel createTeamsPanel() {
		this.teamsPanel = new TeamsPanel(this.gitblit) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void updateUsersTable() {
				GitblitPanel.this.usersPanel.updateTable(false);
			}
		};
		return this.teamsPanel;
	}

	private JPanel createSettingsPanel() {
		this.settingsPanel = new SettingsPanel(this.gitblit);
		return this.settingsPanel;
	}

	private JPanel createStatusPanel() {
		this.statusPanel = new StatusPanel(this.gitblit);
		return this.statusPanel;
	}

	public void login() throws IOException {
		this.gitblit.login();

		this.repositoriesPanel.updateTable(true);
		this.feedsPanel.updateTable(true);

		if (this.gitblit.allowManagement()) {
			if (this.gitblit.getProtocolVersion() >= 2) {
				// refresh teams panel
				this.teamsPanel.updateTable(false);
			} else {
				// remove teams panel
				final String teams = Translation.get("gb.teams");
				for (int i = 0; i < this.tabs.getTabCount(); i++) {
					if (teams.equals(this.tabs.getTitleAt(i))) {
						this.tabs.removeTabAt(i);
						break;
					}
				}
			}
			this.usersPanel.updateTable(false);
		} else {
			// user does not have administrator privileges
			// hide admin repository buttons
			this.repositoriesPanel.disableManagement();

			while (this.tabs.getTabCount() > 2) {
				// remove all management/administration tabs
				this.tabs.removeTabAt(2);
			}
		}

		if (this.gitblit.allowAdministration()) {
			this.settingsPanel.updateTable(true);
			this.statusPanel.updateTable(false);
		} else {
			// remove the settings and status tab
			final String[] titles = { Translation.get("gb.settings"), Translation.get("gb.status") };
			for (final String title : titles) {
				for (int i = 0; i < this.tabs.getTabCount(); i++) {
					if (this.tabs.getTitleAt(i).equals(title)) {
						this.tabs.removeTabAt(i);
						break;
					}
				}
			}
		}
	}

	@Override
	public Insets getInsets() {
		return Utils.INSETS;
	}

	@Override
	public void closeTab(Component c) {
		this.gitblit = null;
	}

	protected void subscribeFeeds(final List<FeedModel> feeds) {
		final SubscriptionsDialog dialog = new SubscriptionsDialog(feeds) {

			private static final long serialVersionUID = 1L;

			@Override
			public void save() {
				GitblitPanel.this.gitblit.updateSubscribedFeeds(feeds);
				GitblitPanel.this.listener.saveRegistration(GitblitPanel.this.gitblit.reg.name,
						GitblitPanel.this.gitblit.reg);
				setVisible(false);
				GitblitPanel.this.repositoriesPanel.updateTable(false);
			}
		};
		dialog.setLocationRelativeTo(GitblitPanel.this);
		dialog.setVisible(true);
	}
}