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
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;

import com.gitblit.Constants;
import com.gitblit.Constants.RpcRequest;
import com.gitblit.Keys;
import com.gitblit.models.FeedModel;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.StringUtils;

/**
 * RSS Feeds Panel displays recent entries and launches the browser to view the
 * commit. commitdiff, or tree of a commit.
 *
 * @author James Moger
 *
 */
public abstract class RepositoriesPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final GitblitClient gitblit;

	private HeaderPanel header;

	private JTable table;

	private RepositoriesTableModel tableModel;

	private TableRowSorter<RepositoriesTableModel> defaultSorter;

	private JButton createRepository;

	private JButton editRepository;

	private JButton delRepository;

	private JTextField filterTextfield;

	private JButton clearCache;

	public RepositoriesPanel(GitblitClient gitblit) {
		super();
		this.gitblit = gitblit;
		initialize();
	}

	private void initialize() {
		final JButton browseRepository = new JButton(Translation.get("gb.browse"));
		browseRepository.setEnabled(false);
		browseRepository.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				final RepositoryModel model = getSelectedRepositories().get(0);
				final String url = RepositoriesPanel.this.gitblit.getURL("summary", model.name,
						null);
				Utils.browse(url);
			}
		});

		final JButton refreshRepositories = new JButton(Translation.get("gb.refresh"));
		refreshRepositories.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				refreshRepositories();
			}
		});

		this.clearCache = new JButton(Translation.get("gb.clearCache"));
		this.clearCache.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				clearCache();
			}
		});

		this.createRepository = new JButton(Translation.get("gb.create"));
		this.createRepository.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				createRepository();
			}
		});

		this.editRepository = new JButton(Translation.get("gb.edit"));
		this.editRepository.setEnabled(false);
		this.editRepository.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				editRepository(getSelectedRepositories().get(0));
			}
		});

		this.delRepository = new JButton(Translation.get("gb.delete"));
		this.delRepository.setEnabled(false);
		this.delRepository.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				deleteRepositories(getSelectedRepositories());
			}
		});

		final JButton subscribeRepository = new JButton(Translation.get("gb.subscribe") + "...");
		subscribeRepository.setEnabled(false);
		subscribeRepository.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				final List<FeedModel> feeds = RepositoriesPanel.this.gitblit
						.getAvailableFeeds(getSelectedRepositories().get(0));
				subscribeFeeds(feeds);
			}
		});

		final JButton logRepository = new JButton(Translation.get("gb.log") + "...");
		logRepository.setEnabled(false);
		logRepository.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				final RepositoryModel model = getSelectedRepositories().get(0);
				showSearchDialog(false, model);
			}
		});

		final JButton searchRepository = new JButton(Translation.get("gb.search") + "...");
		searchRepository.setEnabled(false);
		searchRepository.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				final RepositoryModel model = getSelectedRepositories().get(0);
				showSearchDialog(true, model);
			}
		});

		final SubscribedRepositoryRenderer nameRenderer = new SubscribedRepositoryRenderer(
				this.gitblit);
		final IndicatorsRenderer typeRenderer = new IndicatorsRenderer();

		final DefaultTableCellRenderer sizeRenderer = new DefaultTableCellRenderer();
		sizeRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
		sizeRenderer.setForeground(new Color(0, 0x80, 0));

		final DefaultTableCellRenderer ownerRenderer = new DefaultTableCellRenderer();
		ownerRenderer.setForeground(Color.gray);
		ownerRenderer.setHorizontalAlignment(SwingConstants.CENTER);

		this.tableModel = new RepositoriesTableModel();
		this.defaultSorter = new TableRowSorter<RepositoriesTableModel>(this.tableModel);
		this.table = Utils.newTable(this.tableModel, Utils.DATE_FORMAT);
		this.table.setRowSorter(this.defaultSorter);
		this.table.getRowSorter().toggleSortOrder(RepositoriesTableModel.Columns.Name.ordinal());

		setRepositoryRenderer(RepositoriesTableModel.Columns.Name, nameRenderer, -1);
		setRepositoryRenderer(RepositoriesTableModel.Columns.Indicators, typeRenderer, 100);
		setRepositoryRenderer(RepositoriesTableModel.Columns.Owner, ownerRenderer, -1);
		setRepositoryRenderer(RepositoriesTableModel.Columns.Size, sizeRenderer, 60);

		this.table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) {
					return;
				}
				final boolean singleSelection = RepositoriesPanel.this.table.getSelectedRowCount() == 1;
				final boolean selected = RepositoriesPanel.this.table.getSelectedRow() > -1;
				if (singleSelection) {
					final RepositoryModel repository = getSelectedRepositories().get(0);
					browseRepository.setEnabled(repository.hasCommits);
					logRepository.setEnabled(repository.hasCommits);
					searchRepository.setEnabled(repository.hasCommits);
					subscribeRepository.setEnabled(repository.hasCommits);
				} else {
					browseRepository.setEnabled(false);
					logRepository.setEnabled(false);
					searchRepository.setEnabled(false);
					subscribeRepository.setEnabled(false);
				}
				RepositoriesPanel.this.delRepository.setEnabled(selected);
				if (selected) {
					final int viewRow = RepositoriesPanel.this.table.getSelectedRow();
					final int modelRow = RepositoriesPanel.this.table
							.convertRowIndexToModel(viewRow);
					final RepositoryModel model = ((RepositoriesTableModel) RepositoriesPanel.this.table
							.getModel()).list.get(modelRow);
					RepositoriesPanel.this.editRepository.setEnabled(singleSelection
							&& (RepositoriesPanel.this.gitblit.allowManagement() || RepositoriesPanel.this.gitblit
									.isOwner(model)));
				} else {
					RepositoriesPanel.this.editRepository.setEnabled(false);
				}
			}
		});

		this.table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if ((e.getClickCount() == 2) && RepositoriesPanel.this.gitblit.allowManagement()) {
					editRepository(getSelectedRepositories().get(0));
				}
			}
		});

		this.filterTextfield = new JTextField();
		this.filterTextfield.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				filterRepositories(RepositoriesPanel.this.filterTextfield.getText());
			}
		});
		this.filterTextfield.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				filterRepositories(RepositoriesPanel.this.filterTextfield.getText());
			}
		});

		final JPanel repositoryFilterPanel = new JPanel(
				new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		repositoryFilterPanel.add(new JLabel(Translation.get("gb.filter")), BorderLayout.WEST);
		repositoryFilterPanel.add(this.filterTextfield, BorderLayout.CENTER);

		final JPanel repositoryTablePanel = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		repositoryTablePanel.add(repositoryFilterPanel, BorderLayout.NORTH);
		repositoryTablePanel.add(new JScrollPane(this.table), BorderLayout.CENTER);

		final JPanel repositoryControls = new JPanel(new FlowLayout(FlowLayout.CENTER,
				Utils.MARGIN, 0));
		repositoryControls.add(this.clearCache);
		repositoryControls.add(refreshRepositories);
		repositoryControls.add(browseRepository);
		repositoryControls.add(this.createRepository);
		repositoryControls.add(this.editRepository);
		repositoryControls.add(this.delRepository);
		repositoryControls.add(subscribeRepository);
		repositoryControls.add(logRepository);
		repositoryControls.add(searchRepository);

		setLayout(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		this.header = new HeaderPanel(Translation.get("gb.repositories"), "git-orange-16x16.png");
		add(this.header, BorderLayout.NORTH);
		add(repositoryTablePanel, BorderLayout.CENTER);
		add(repositoryControls, BorderLayout.SOUTH);
	}

	@Override
	public void requestFocus() {
		this.filterTextfield.requestFocus();
	}

	@Override
	public Insets getInsets() {
		return Utils.INSETS;
	}

	private void setRepositoryRenderer(RepositoriesTableModel.Columns col,
			TableCellRenderer renderer, int maxWidth) {
		final String name = this.table.getColumnName(col.ordinal());
		this.table.getColumn(name).setCellRenderer(renderer);
		if (maxWidth > 0) {
			this.table.getColumn(name).setMinWidth(maxWidth);
			this.table.getColumn(name).setMaxWidth(maxWidth);
		}
	}

	protected abstract void subscribeFeeds(List<FeedModel> feeds);

	protected abstract void updateUsersTable();

	protected abstract void updateTeamsTable();

	protected void disableManagement() {
		this.clearCache.setVisible(false);
		this.createRepository.setVisible(false);
		this.editRepository.setVisible(false);
		this.delRepository.setVisible(false);
	}

	protected void updateTable(boolean pack) {
		this.tableModel.list.clear();
		this.tableModel.list.addAll(this.gitblit.getRepositories());
		this.tableModel.fireTableDataChanged();
		this.header.setText(Translation.get("gb.repositories") + " ("
				+ this.gitblit.getRepositories().size() + ")");
		if (pack) {
			Utils.packColumns(this.table, Utils.MARGIN);
		}
	}

	private void filterRepositories(final String fragment) {
		if (StringUtils.isEmpty(fragment)) {
			this.table.setRowSorter(this.defaultSorter);
			return;
		}
		final RowFilter<RepositoriesTableModel, Object> containsFilter = new RowFilter<RepositoriesTableModel, Object>() {
			@Override
			public boolean include(Entry<? extends RepositoriesTableModel, ? extends Object> entry) {
				for (int i = entry.getValueCount() - 1; i >= 0; i--) {
					if (entry.getStringValue(i).toLowerCase().contains(fragment.toLowerCase())) {
						return true;
					}
				}
				return false;
			}
		};
		final TableRowSorter<RepositoriesTableModel> sorter = new TableRowSorter<RepositoriesTableModel>(
				this.tableModel);
		sorter.setRowFilter(containsFilter);
		this.table.setRowSorter(sorter);
	}

	private List<RepositoryModel> getSelectedRepositories() {
		final List<RepositoryModel> repositories = new ArrayList<RepositoryModel>();
		for (final int viewRow : this.table.getSelectedRows()) {
			final int modelRow = this.table.convertRowIndexToModel(viewRow);
			final RepositoryModel model = this.tableModel.list.get(modelRow);
			repositories.add(model);
		}
		return repositories;
	}

	protected void refreshRepositories() {
		final GitblitWorker worker = new GitblitWorker(RepositoriesPanel.this,
				RpcRequest.LIST_REPOSITORIES) {
			@Override
			protected Boolean doRequest() throws IOException {
				RepositoriesPanel.this.gitblit.refreshRepositories();
				return true;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
			}
		};
		worker.execute();
	}

	protected void clearCache() {
		final GitblitWorker worker = new GitblitWorker(RepositoriesPanel.this,
				RpcRequest.CLEAR_REPOSITORY_CACHE) {
			@Override
			protected Boolean doRequest() throws IOException {
				if (RepositoriesPanel.this.gitblit.clearRepositoryCache()) {
					RepositoriesPanel.this.gitblit.refreshRepositories();
					return true;
				}
				return false;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
			}
		};
		worker.execute();
	}

	/**
	 * Displays the create repository dialog and fires a SwingWorker to update
	 * the server, if appropriate.
	 *
	 */
	protected void createRepository() {
		final EditRepositoryDialog dialog = new EditRepositoryDialog(
				this.gitblit.getProtocolVersion());
		dialog.setLocationRelativeTo(RepositoriesPanel.this);
		dialog.setAccessRestriction(this.gitblit.getDefaultAccessRestriction());
		dialog.setAuthorizationControl(this.gitblit.getDefaultAuthorizationControl());
		dialog.setUsers(null, this.gitblit.getUsernames(), null);
		dialog.setTeams(this.gitblit.getTeamnames(), null);
		dialog.setRepositories(this.gitblit.getRepositories());
		dialog.setFederationSets(this.gitblit.getFederationSets(), null);
		dialog.setIndexedBranches(new ArrayList<String>(Arrays.asList(Constants.DEFAULT_BRANCH)),
				null);
		dialog.setPreReceiveScripts(this.gitblit.getPreReceiveScriptsUnused(null),
				this.gitblit.getPreReceiveScriptsInherited(null), null);
		dialog.setPostReceiveScripts(this.gitblit.getPostReceiveScriptsUnused(null),
				this.gitblit.getPostReceiveScriptsInherited(null), null);
		dialog.setVisible(true);
		final RepositoryModel newRepository = dialog.getRepository();
		final List<RegistrantAccessPermission> permittedUsers = dialog.getUserAccessPermissions();
		final List<RegistrantAccessPermission> permittedTeams = dialog.getTeamAccessPermissions();
		if (newRepository == null) {
			return;
		}

		final GitblitWorker worker = new GitblitWorker(this, RpcRequest.CREATE_REPOSITORY) {

			@Override
			protected Boolean doRequest() throws IOException {
				final boolean success = RepositoriesPanel.this.gitblit.createRepository(
						newRepository, permittedUsers, permittedTeams);
				if (success) {
					RepositoriesPanel.this.gitblit.refreshRepositories();
					if (permittedUsers.size() > 0) {
						RepositoriesPanel.this.gitblit.refreshUsers();
					}
					if (permittedTeams.size() > 0) {
						RepositoriesPanel.this.gitblit.refreshTeams();
					}
				}
				return success;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
				updateUsersTable();
				updateTeamsTable();
			}

			@Override
			protected void onFailure() {
				showFailure("Failed to execute request \"{0}\" for repository \"{1}\".",
						getRequestType(), newRepository.name);
			}
		};
		worker.execute();
	}

	/**
	 * Displays the edit repository dialog and fires a SwingWorker to update the
	 * server, if appropriate.
	 *
	 * @param repository
	 */
	protected void editRepository(final RepositoryModel repository) {
		final EditRepositoryDialog dialog = new EditRepositoryDialog(
				this.gitblit.getProtocolVersion(), repository);
		dialog.setLocationRelativeTo(RepositoriesPanel.this);
		final List<String> usernames = this.gitblit.getUsernames();
		final List<RegistrantAccessPermission> members = this.gitblit
				.getUserAccessPermissions(repository);
		dialog.setUsers(new ArrayList<String>(repository.owners), usernames, members);
		dialog.setTeams(this.gitblit.getTeamnames(),
				this.gitblit.getTeamAccessPermissions(repository));
		dialog.setRepositories(this.gitblit.getRepositories());
		dialog.setFederationSets(this.gitblit.getFederationSets(), repository.federationSets);
		final List<String> allLocalBranches = new ArrayList<String>();
		allLocalBranches.add(Constants.DEFAULT_BRANCH);
		allLocalBranches.addAll(repository.getLocalBranches());
		dialog.setIndexedBranches(allLocalBranches, repository.indexedBranches);
		dialog.setPreReceiveScripts(this.gitblit.getPreReceiveScriptsUnused(repository),
				this.gitblit.getPreReceiveScriptsInherited(repository),
				repository.preReceiveScripts);
		dialog.setPostReceiveScripts(this.gitblit.getPostReceiveScriptsUnused(repository),
				this.gitblit.getPostReceiveScriptsInherited(repository),
				repository.postReceiveScripts);
		if (this.gitblit.getSettings().hasKey(Keys.groovy.customFields)) {
			final Map<String, String> map = this.gitblit.getSettings()
					.get(Keys.groovy.customFields).getMap();
			dialog.setCustomFields(repository, map);
		}
		dialog.setVisible(true);
		final RepositoryModel revisedRepository = dialog.getRepository();
		final List<RegistrantAccessPermission> permittedUsers = dialog.getUserAccessPermissions();
		final List<RegistrantAccessPermission> permittedTeams = dialog.getTeamAccessPermissions();
		if (revisedRepository == null) {
			return;
		}

		final GitblitWorker worker = new GitblitWorker(this, RpcRequest.EDIT_REPOSITORY) {

			@Override
			protected Boolean doRequest() throws IOException {
				final boolean success = RepositoriesPanel.this.gitblit.updateRepository(
						repository.name, revisedRepository, permittedUsers, permittedTeams);
				if (success) {
					RepositoriesPanel.this.gitblit.refreshRepositories();
					RepositoriesPanel.this.gitblit.refreshUsers();
					RepositoriesPanel.this.gitblit.refreshTeams();
				}
				return success;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
				updateUsersTable();
				updateTeamsTable();
			}

			@Override
			protected void onFailure() {
				showFailure("Failed to execute request \"{0}\" for repository \"{1}\".",
						getRequestType(), repository.name);
			}
		};
		worker.execute();
	}

	protected void deleteRepositories(final List<RepositoryModel> repositories) {
		if ((repositories == null) || (repositories.size() == 0)) {
			return;
		}
		final StringBuilder message = new StringBuilder("Delete the following repositories?\n\n");
		for (final RepositoryModel repository : repositories) {
			message.append(repository.name).append("\n");
		}
		final int result = JOptionPane.showConfirmDialog(RepositoriesPanel.this,
				message.toString(), "Delete Repositories?", JOptionPane.YES_NO_OPTION);
		if (result == JOptionPane.YES_OPTION) {
			final GitblitWorker worker = new GitblitWorker(this, RpcRequest.DELETE_REPOSITORY) {
				@Override
				protected Boolean doRequest() throws IOException {
					boolean success = true;
					for (final RepositoryModel repository : repositories) {
						success &= RepositoriesPanel.this.gitblit.deleteRepository(repository);
					}
					if (success) {
						RepositoriesPanel.this.gitblit.refreshRepositories();
						RepositoriesPanel.this.gitblit.refreshUsers();
						RepositoriesPanel.this.gitblit.refreshTeams();
					}
					return success;
				}

				@Override
				protected void onSuccess() {
					updateTable(false);
					updateUsersTable();
					updateTeamsTable();
				}

				@Override
				protected void onFailure() {
					showFailure("Failed to delete specified repositories!");
				}
			};
			worker.execute();
		}
	}

	private void showSearchDialog(boolean isSearch, final RepositoryModel repository) {
		final SearchDialog dialog = new SearchDialog(this.gitblit, isSearch);
		if (repository != null) {
			dialog.selectRepository(repository);
		}
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}
}
