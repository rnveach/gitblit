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
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableRowSorter;

import com.gitblit.Constants.RpcRequest;
import com.gitblit.models.TeamModel;
import com.gitblit.utils.StringUtils;

/**
 * Users panel displays a list of user accounts and allows management of those
 * accounts.
 *
 * @author James Moger
 *
 */
public abstract class TeamsPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final GitblitClient gitblit;

	private HeaderPanel header;

	private JTable table;

	private TeamsTableModel tableModel;

	private TableRowSorter<TeamsTableModel> defaultSorter;

	private JTextField filterTextfield;

	public TeamsPanel(GitblitClient gitblit) {
		super();
		this.gitblit = gitblit;
		initialize();
	}

	private void initialize() {
		final JButton refreshTeams = new JButton(Translation.get("gb.refresh"));
		refreshTeams.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				refreshTeams();
			}
		});

		final JButton createTeam = new JButton(Translation.get("gb.create"));
		createTeam.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				createTeam();
			}
		});

		final JButton editTeam = new JButton(Translation.get("gb.edit"));
		editTeam.setEnabled(false);
		editTeam.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				editTeam(getSelectedTeams().get(0));
			}
		});

		final JButton delTeam = new JButton(Translation.get("gb.delete"));
		delTeam.setEnabled(false);
		delTeam.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				deleteTeams(getSelectedTeams());
			}
		});

		final NameRenderer nameRenderer = new NameRenderer();
		this.tableModel = new TeamsTableModel();
		this.defaultSorter = new TableRowSorter<TeamsTableModel>(this.tableModel);
		this.table = Utils.newTable(this.tableModel, Utils.DATE_FORMAT);
		String name = this.table.getColumnName(TeamsTableModel.Columns.Name.ordinal());
		this.table.getColumn(name).setCellRenderer(nameRenderer);

		final int w = 125;
		name = this.table.getColumnName(TeamsTableModel.Columns.Members.ordinal());
		this.table.getColumn(name).setMinWidth(w);
		this.table.getColumn(name).setMaxWidth(w);
		name = this.table.getColumnName(TeamsTableModel.Columns.Repositories.ordinal());
		this.table.getColumn(name).setMinWidth(w);
		this.table.getColumn(name).setMaxWidth(w);

		this.table.setRowSorter(this.defaultSorter);
		this.table.getRowSorter().toggleSortOrder(TeamsTableModel.Columns.Name.ordinal());
		this.table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) {
					return;
				}
				final boolean selected = TeamsPanel.this.table.getSelectedRow() > -1;
				final boolean singleSelection = TeamsPanel.this.table.getSelectedRows().length == 1;
				editTeam.setEnabled(singleSelection && selected);
				delTeam.setEnabled(selected);
			}
		});

		this.table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					editTeam(getSelectedTeams().get(0));
				}
			}
		});

		this.filterTextfield = new JTextField();
		this.filterTextfield.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				filterTeams(TeamsPanel.this.filterTextfield.getText());
			}
		});
		this.filterTextfield.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				filterTeams(TeamsPanel.this.filterTextfield.getText());
			}
		});

		final JPanel teamFilterPanel = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		teamFilterPanel.add(new JLabel(Translation.get("gb.filter")), BorderLayout.WEST);
		teamFilterPanel.add(this.filterTextfield, BorderLayout.CENTER);

		final JPanel teamTablePanel = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		teamTablePanel.add(teamFilterPanel, BorderLayout.NORTH);
		teamTablePanel.add(new JScrollPane(this.table), BorderLayout.CENTER);

		final JPanel teamControls = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
		teamControls.add(refreshTeams);
		teamControls.add(createTeam);
		teamControls.add(editTeam);
		teamControls.add(delTeam);

		setLayout(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		this.header = new HeaderPanel(Translation.get("gb.teams"), "users_16x16.png");
		add(this.header, BorderLayout.NORTH);
		add(teamTablePanel, BorderLayout.CENTER);
		add(teamControls, BorderLayout.SOUTH);
	}

	@Override
	public void requestFocus() {
		this.filterTextfield.requestFocus();
	}

	@Override
	public Insets getInsets() {
		return Utils.INSETS;
	}

	protected abstract void updateUsersTable();

	protected void updateTable(boolean pack) {
		this.tableModel.list.clear();
		this.tableModel.list.addAll(this.gitblit.getTeams());
		this.tableModel.fireTableDataChanged();
		this.header.setText(Translation.get("gb.teams") + " (" + this.gitblit.getTeams().size()
				+ ")");
		if (pack) {
			Utils.packColumns(this.table, Utils.MARGIN);
		}
	}

	private void filterTeams(final String fragment) {
		if (StringUtils.isEmpty(fragment)) {
			this.table.setRowSorter(this.defaultSorter);
			return;
		}
		final RowFilter<TeamsTableModel, Object> containsFilter = new RowFilter<TeamsTableModel, Object>() {
			@Override
			public boolean include(Entry<? extends TeamsTableModel, ? extends Object> entry) {
				for (int i = entry.getValueCount() - 1; i >= 0; i--) {
					if (entry.getStringValue(i).toLowerCase().contains(fragment.toLowerCase())) {
						return true;
					}
				}
				return false;
			}
		};
		final TableRowSorter<TeamsTableModel> sorter = new TableRowSorter<TeamsTableModel>(
				this.tableModel);
		sorter.setRowFilter(containsFilter);
		this.table.setRowSorter(sorter);
	}

	private List<TeamModel> getSelectedTeams() {
		final List<TeamModel> teams = new ArrayList<TeamModel>();
		for (final int viewRow : this.table.getSelectedRows()) {
			final int modelRow = this.table.convertRowIndexToModel(viewRow);
			final TeamModel model = this.tableModel.list.get(modelRow);
			teams.add(model);
		}
		return teams;
	}

	protected void refreshTeams() {
		final GitblitWorker worker = new GitblitWorker(TeamsPanel.this, RpcRequest.LIST_TEAMS) {
			@Override
			protected Boolean doRequest() throws IOException {
				TeamsPanel.this.gitblit.refreshTeams();
				return true;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
			}
		};
		worker.execute();
	}

	/**
	 * Displays the create team dialog and fires a SwingWorker to update the
	 * server, if appropriate.
	 *
	 */
	protected void createTeam() {
		final EditTeamDialog dialog = new EditTeamDialog(this.gitblit.getProtocolVersion(),
				this.gitblit.getSettings());
		dialog.setLocationRelativeTo(TeamsPanel.this);
		dialog.setTeams(this.gitblit.getTeams());
		dialog.setRepositories(this.gitblit.getRepositories(), null);
		dialog.setUsers(this.gitblit.getUsernames(), null);
		dialog.setPreReceiveScripts(this.gitblit.getPreReceiveScriptsUnused(null),
				this.gitblit.getPreReceiveScriptsInherited(null), null);
		dialog.setPostReceiveScripts(this.gitblit.getPostReceiveScriptsUnused(null),
				this.gitblit.getPostReceiveScriptsInherited(null), null);
		dialog.setVisible(true);
		final TeamModel newTeam = dialog.getTeam();
		if (newTeam == null) {
			return;
		}

		final GitblitWorker worker = new GitblitWorker(this, RpcRequest.CREATE_TEAM) {

			@Override
			protected Boolean doRequest() throws IOException {
				final boolean success = TeamsPanel.this.gitblit.createTeam(newTeam);
				if (success) {
					TeamsPanel.this.gitblit.refreshTeams();
					TeamsPanel.this.gitblit.refreshUsers();
				}
				return success;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
				updateUsersTable();
			}

			@Override
			protected void onFailure() {
				showFailure("Failed to execute request \"{0}\" for team \"{1}\".",
						getRequestType(), newTeam.name);
			}
		};
		worker.execute();
	}

	/**
	 * Displays the edit team dialog and fires a SwingWorker to update the
	 * server, if appropriate.
	 *
	 * @param user
	 */
	protected void editTeam(final TeamModel team) {
		final EditTeamDialog dialog = new EditTeamDialog(this.gitblit.getProtocolVersion(), team,
				this.gitblit.getSettings());
		dialog.setLocationRelativeTo(TeamsPanel.this);
		dialog.setTeams(this.gitblit.getTeams());
		dialog.setRepositories(this.gitblit.getRepositories(), team.getRepositoryPermissions());
		dialog.setUsers(this.gitblit.getUsernames(), team.users == null ? null
				: new ArrayList<String>(team.users));
		dialog.setPreReceiveScripts(this.gitblit.getPreReceiveScriptsUnused(null),
				this.gitblit.getPreReceiveScriptsInherited(null), team.preReceiveScripts);
		dialog.setPostReceiveScripts(this.gitblit.getPostReceiveScriptsUnused(null),
				this.gitblit.getPostReceiveScriptsInherited(null), team.postReceiveScripts);
		dialog.setVisible(true);
		final TeamModel revisedTeam = dialog.getTeam();
		if (revisedTeam == null) {
			return;
		}

		final GitblitWorker worker = new GitblitWorker(this, RpcRequest.EDIT_TEAM) {
			@Override
			protected Boolean doRequest() throws IOException {
				final boolean success = TeamsPanel.this.gitblit.updateTeam(team.name, revisedTeam);
				if (success) {
					TeamsPanel.this.gitblit.refreshTeams();
					TeamsPanel.this.gitblit.refreshUsers();
				}
				return success;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
				updateUsersTable();
			}

			@Override
			protected void onFailure() {
				showFailure("Failed to execute request \"{0}\" for team \"{1}\".",
						getRequestType(), team.name);
			}
		};
		worker.execute();
	}

	protected void deleteTeams(final List<TeamModel> teams) {
		if ((teams == null) || (teams.size() == 0)) {
			return;
		}
		final StringBuilder message = new StringBuilder("Delete the following teams?\n\n");
		for (final TeamModel team : teams) {
			message.append(team.name).append("\n");
		}
		final int result = JOptionPane.showConfirmDialog(TeamsPanel.this, message.toString(),
				"Delete Teams?", JOptionPane.YES_NO_OPTION);
		if (result == JOptionPane.YES_OPTION) {
			final GitblitWorker worker = new GitblitWorker(this, RpcRequest.DELETE_TEAM) {
				@Override
				protected Boolean doRequest() throws IOException {
					boolean success = true;
					for (final TeamModel team : teams) {
						success &= TeamsPanel.this.gitblit.deleteTeam(team);
					}
					if (success) {
						TeamsPanel.this.gitblit.refreshTeams();
						TeamsPanel.this.gitblit.refreshUsers();
					}
					return success;
				}

				@Override
				protected void onSuccess() {
					updateTable(false);
					updateUsersTable();
				}

				@Override
				protected void onFailure() {
					showFailure("Failed to delete specified teams!");
				}
			};
			worker.execute();
		}
	}
}
