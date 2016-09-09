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
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;

/**
 * Users panel displays a list of user accounts and allows management of those
 * accounts.
 *
 * @author James Moger
 *
 */
public abstract class UsersPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final GitblitClient gitblit;

	private HeaderPanel header;

	private JTable table;

	private UsersTableModel tableModel;

	private TableRowSorter<UsersTableModel> defaultSorter;

	private JTextField filterTextfield;

	public UsersPanel(GitblitClient gitblit) {
		super();
		this.gitblit = gitblit;
		initialize();
	}

	private void initialize() {
		final JButton refreshUsers = new JButton(Translation.get("gb.refresh"));
		refreshUsers.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				refreshUsers();
			}
		});

		final JButton createUser = new JButton(Translation.get("gb.create"));
		createUser.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				createUser();
			}
		});

		final JButton editUser = new JButton(Translation.get("gb.edit"));
		editUser.setEnabled(false);
		editUser.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				editUser(getSelectedUsers().get(0));
			}
		});

		final JButton delUser = new JButton(Translation.get("gb.delete"));
		delUser.setEnabled(false);
		delUser.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				deleteUsers(getSelectedUsers());
			}
		});

		final NameRenderer nameRenderer = new NameRenderer();
		this.tableModel = new UsersTableModel();
		this.defaultSorter = new TableRowSorter<UsersTableModel>(this.tableModel);
		this.table = Utils.newTable(this.tableModel, Utils.DATE_FORMAT);
		String name = this.table.getColumnName(UsersTableModel.Columns.Name.ordinal());
		this.table.getColumn(name).setCellRenderer(nameRenderer);

		final int w = 130;
		name = this.table.getColumnName(UsersTableModel.Columns.Type.ordinal());
		this.table.getColumn(name).setMinWidth(w);
		this.table.getColumn(name).setMaxWidth(w);
		name = this.table.getColumnName(UsersTableModel.Columns.Teams.ordinal());
		this.table.getColumn(name).setMinWidth(w);
		this.table.getColumn(name).setMaxWidth(w);
		name = this.table.getColumnName(UsersTableModel.Columns.Repositories.ordinal());
		this.table.getColumn(name).setMinWidth(w);
		this.table.getColumn(name).setMaxWidth(w);

		this.table.setRowSorter(this.defaultSorter);
		this.table.getRowSorter().toggleSortOrder(UsersTableModel.Columns.Name.ordinal());
		this.table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) {
					return;
				}
				final boolean selected = UsersPanel.this.table.getSelectedRow() > -1;
				final boolean singleSelection = UsersPanel.this.table.getSelectedRows().length == 1;
				editUser.setEnabled(singleSelection && selected);
				delUser.setEnabled(selected);
			}
		});

		this.table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					editUser(getSelectedUsers().get(0));
				}
			}
		});

		this.filterTextfield = new JTextField();
		this.filterTextfield.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				filterUsers(UsersPanel.this.filterTextfield.getText());
			}
		});
		this.filterTextfield.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				filterUsers(UsersPanel.this.filterTextfield.getText());
			}
		});

		final JPanel userFilterPanel = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		userFilterPanel.add(new JLabel(Translation.get("gb.filter")), BorderLayout.WEST);
		userFilterPanel.add(this.filterTextfield, BorderLayout.CENTER);

		final JPanel userTablePanel = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		userTablePanel.add(userFilterPanel, BorderLayout.NORTH);
		userTablePanel.add(new JScrollPane(this.table), BorderLayout.CENTER);

		final JPanel userControls = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
		userControls.add(refreshUsers);
		userControls.add(createUser);
		userControls.add(editUser);
		userControls.add(delUser);

		setLayout(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		this.header = new HeaderPanel(Translation.get("gb.users"), "user_16x16.png");
		add(this.header, BorderLayout.NORTH);
		add(userTablePanel, BorderLayout.CENTER);
		add(userControls, BorderLayout.SOUTH);
	}

	@Override
	public void requestFocus() {
		this.filterTextfield.requestFocus();
	}

	@Override
	public Insets getInsets() {
		return Utils.INSETS;
	}

	protected abstract void updateTeamsTable();

	protected void updateTable(boolean pack) {
		this.tableModel.list.clear();
		this.tableModel.list.addAll(this.gitblit.getUsers());
		this.tableModel.fireTableDataChanged();
		this.header.setText(Translation.get("gb.users") + " (" + this.gitblit.getUsers().size()
				+ ")");
		if (pack) {
			Utils.packColumns(this.table, Utils.MARGIN);
		}
	}

	private void filterUsers(final String fragment) {
		if (StringUtils.isEmpty(fragment)) {
			this.table.setRowSorter(this.defaultSorter);
			return;
		}
		final RowFilter<UsersTableModel, Object> containsFilter = new RowFilter<UsersTableModel, Object>() {
			@Override
			public boolean include(Entry<? extends UsersTableModel, ? extends Object> entry) {
				for (int i = entry.getValueCount() - 1; i >= 0; i--) {
					if (entry.getStringValue(i).toLowerCase().contains(fragment.toLowerCase())) {
						return true;
					}
				}
				return false;
			}
		};
		final TableRowSorter<UsersTableModel> sorter = new TableRowSorter<UsersTableModel>(
				this.tableModel);
		sorter.setRowFilter(containsFilter);
		this.table.setRowSorter(sorter);
	}

	private List<UserModel> getSelectedUsers() {
		final List<UserModel> users = new ArrayList<UserModel>();
		for (final int viewRow : this.table.getSelectedRows()) {
			final int modelRow = this.table.convertRowIndexToModel(viewRow);
			final UserModel model = this.tableModel.list.get(modelRow);
			users.add(model);
		}
		return users;
	}

	protected void refreshUsers() {
		final GitblitWorker worker = new GitblitWorker(UsersPanel.this, RpcRequest.LIST_USERS) {
			@Override
			protected Boolean doRequest() throws IOException {
				UsersPanel.this.gitblit.refreshUsers();
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
	 * Displays the create user dialog and fires a SwingWorker to update the
	 * server, if appropriate.
	 *
	 */
	protected void createUser() {
		final EditUserDialog dialog = new EditUserDialog(this.gitblit.getProtocolVersion(),
				this.gitblit.getSettings());
		dialog.setLocationRelativeTo(UsersPanel.this);
		dialog.setUsers(this.gitblit.getUsers());
		dialog.setRepositories(this.gitblit.getRepositories(), null);
		dialog.setTeams(this.gitblit.getTeams(), null);
		dialog.setVisible(true);
		final UserModel newUser = dialog.getUser();
		if (newUser == null) {
			return;
		}

		final GitblitWorker worker = new GitblitWorker(this, RpcRequest.CREATE_USER) {

			@Override
			protected Boolean doRequest() throws IOException {
				final boolean success = UsersPanel.this.gitblit.createUser(newUser);
				if (success) {
					UsersPanel.this.gitblit.refreshUsers();
					if (newUser.teams.size() > 0) {
						UsersPanel.this.gitblit.refreshTeams();
					}
				}
				return success;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
				if (newUser.teams.size() > 0) {
					updateTeamsTable();
				}
			}

			@Override
			protected void onFailure() {
				showFailure("Failed to execute request \"{0}\" for user \"{1}\".",
						getRequestType(), newUser.username);
			}
		};
		worker.execute();
	}

	/**
	 * Displays the edit user dialog and fires a SwingWorker to update the
	 * server, if appropriate.
	 *
	 * @param user
	 */
	protected void editUser(final UserModel user) {
		final EditUserDialog dialog = new EditUserDialog(this.gitblit.getProtocolVersion(), user,
				this.gitblit.getSettings());
		dialog.setLocationRelativeTo(UsersPanel.this);
		dialog.setUsers(this.gitblit.getUsers());
		dialog.setRepositories(this.gitblit.getRepositories(),
				this.gitblit.getUserAccessPermissions(user));
		dialog.setTeams(this.gitblit.getTeams(), user.teams == null ? null
				: new ArrayList<TeamModel>(user.teams));
		dialog.setVisible(true);
		final UserModel revisedUser = dialog.getUser();
		if (revisedUser == null) {
			return;
		}

		final GitblitWorker worker = new GitblitWorker(this, RpcRequest.EDIT_USER) {
			@Override
			protected Boolean doRequest() throws IOException {
				final boolean success = UsersPanel.this.gitblit.updateUser(user.username,
						revisedUser);
				if (success) {
					UsersPanel.this.gitblit.refreshUsers();
					UsersPanel.this.gitblit.refreshTeams();
				}
				return success;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
				updateTeamsTable();
			}

			@Override
			protected void onFailure() {
				showFailure("Failed to execute request \"{0}\" for user \"{1}\".",
						getRequestType(), user.username);
			}
		};
		worker.execute();
	}

	protected void deleteUsers(final List<UserModel> users) {
		if ((users == null) || (users.size() == 0)) {
			return;
		}
		final StringBuilder message = new StringBuilder("Delete the following users?\n\n");
		for (final UserModel user : users) {
			message.append(user.username).append("\n");
		}
		final int result = JOptionPane.showConfirmDialog(UsersPanel.this, message.toString(),
				"Delete Users?", JOptionPane.YES_NO_OPTION);
		if (result == JOptionPane.YES_OPTION) {
			final GitblitWorker worker = new GitblitWorker(this, RpcRequest.DELETE_USER) {
				@Override
				protected Boolean doRequest() throws IOException {
					boolean success = true;
					for (final UserModel user : users) {
						success &= UsersPanel.this.gitblit.deleteUser(user);
					}
					if (success) {
						UsersPanel.this.gitblit.refreshUsers();
						UsersPanel.this.gitblit.refreshTeams();
					}
					return success;
				}

				@Override
				protected void onSuccess() {
					updateTable(false);
					updateTeamsTable();
				}

				@Override
				protected void onFailure() {
					showFailure("Failed to delete specified users!");
				}
			};
			worker.execute();
		}
	}
}
