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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.swing.ImageIcon;
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
import com.gitblit.models.SettingModel;
import com.gitblit.utils.StringUtils;

/**
 * Settings panel displays a list of server settings and their associated
 * metadata. This panel also allows editing of a setting.
 *
 * @author James Moger
 *
 */
public class SettingsPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final GitblitClient gitblit;

	private HeaderPanel header;

	private JTable table;

	private SettingsTableModel tableModel;

	private TableRowSorter<SettingsTableModel> defaultSorter;

	private JTextField filterTextfield;

	public SettingsPanel(GitblitClient gitblit) {
		super();
		this.gitblit = gitblit;
		initialize();
	}

	private void initialize() {
		final JButton refreshSettings = new JButton(Translation.get("gb.refresh"));
		refreshSettings.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				refreshSettings();
			}
		});

		final JButton editSetting = new JButton(Translation.get("gb.edit"));
		editSetting.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				final int viewRow = SettingsPanel.this.table.getSelectedRow();
				final int modelRow = SettingsPanel.this.table.convertRowIndexToModel(viewRow);
				final String key = SettingsPanel.this.tableModel.keys.get(modelRow);
				final SettingModel setting = SettingsPanel.this.tableModel.settings.get(key);
				editSetting(setting);
			}
		});

		final NameRenderer nameRenderer = new NameRenderer();
		final SettingPanel settingPanel = new SettingPanel();
		this.tableModel = new SettingsTableModel();
		this.defaultSorter = new TableRowSorter<SettingsTableModel>(this.tableModel);
		this.table = Utils.newTable(this.tableModel, Utils.DATE_FORMAT);
		this.table.setDefaultRenderer(SettingModel.class, new SettingCellRenderer());
		final String name = this.table.getColumnName(UsersTableModel.Columns.Name.ordinal());
		this.table.getColumn(name).setCellRenderer(nameRenderer);
		this.table.setRowSorter(this.defaultSorter);
		this.table.getRowSorter().toggleSortOrder(SettingsTableModel.Columns.Name.ordinal());
		this.table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) {
					return;
				}
				final boolean singleSelection = SettingsPanel.this.table.getSelectedRows().length == 1;
				editSetting.setEnabled(singleSelection);
				if (singleSelection) {
					final int viewRow = SettingsPanel.this.table.getSelectedRow();
					final int modelRow = SettingsPanel.this.table.convertRowIndexToModel(viewRow);
					final SettingModel setting = SettingsPanel.this.tableModel.get(modelRow);
					settingPanel.setSetting(setting);
				} else {
					settingPanel.clear();
				}
			}
		});
		this.table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					final int viewRow = SettingsPanel.this.table.getSelectedRow();
					final int modelRow = SettingsPanel.this.table.convertRowIndexToModel(viewRow);
					final SettingModel setting = SettingsPanel.this.tableModel.get(modelRow);
					editSetting(setting);
				}
			}
		});

		this.filterTextfield = new JTextField();
		this.filterTextfield.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				filterSettings(SettingsPanel.this.filterTextfield.getText());
			}
		});
		this.filterTextfield.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				filterSettings(SettingsPanel.this.filterTextfield.getText());
			}
		});

		final JPanel settingFilterPanel = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		settingFilterPanel.add(new JLabel(Translation.get("gb.filter")), BorderLayout.WEST);
		settingFilterPanel.add(this.filterTextfield, BorderLayout.CENTER);

		final JPanel settingsTablePanel = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		settingsTablePanel.add(settingFilterPanel, BorderLayout.NORTH);
		settingsTablePanel.add(new JScrollPane(this.table), BorderLayout.CENTER);
		settingsTablePanel.add(settingPanel, BorderLayout.SOUTH);

		final JPanel settingsControls = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
		settingsControls.add(refreshSettings);
		settingsControls.add(editSetting);

		setLayout(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		this.header = new HeaderPanel(Translation.get("gb.settings"), "settings_16x16.png");
		add(this.header, BorderLayout.NORTH);
		add(settingsTablePanel, BorderLayout.CENTER);
		add(settingsControls, BorderLayout.SOUTH);
	}

	@Override
	public void requestFocus() {
		this.filterTextfield.requestFocus();
	}

	@Override
	public Insets getInsets() {
		return Utils.INSETS;
	}

	protected void updateTable(boolean pack) {
		this.tableModel.setSettings(this.gitblit.getSettings());
		this.tableModel.fireTableDataChanged();
		this.header.setText(Translation.get("gb.settings"));
		if (pack) {
			Utils.packColumns(this.table, Utils.MARGIN);
		}
	}

	private void filterSettings(final String fragment) {
		if (StringUtils.isEmpty(fragment)) {
			this.table.setRowSorter(this.defaultSorter);
			return;
		}
		final RowFilter<SettingsTableModel, Object> containsFilter = new RowFilter<SettingsTableModel, Object>() {
			@Override
			public boolean include(Entry<? extends SettingsTableModel, ? extends Object> entry) {
				for (int i = entry.getValueCount() - 1; i >= 0; i--) {
					if (entry.getStringValue(i).toLowerCase().contains(fragment.toLowerCase())) {
						return true;
					}
				}
				return false;
			}
		};
		final TableRowSorter<SettingsTableModel> sorter = new TableRowSorter<SettingsTableModel>(
				this.tableModel);
		sorter.setRowFilter(containsFilter);
		this.table.setRowSorter(sorter);
	}

	protected void refreshSettings() {
		final GitblitWorker worker = new GitblitWorker(SettingsPanel.this, RpcRequest.LIST_SETTINGS) {
			@Override
			protected Boolean doRequest() throws IOException {
				SettingsPanel.this.gitblit.refreshSettings();
				return true;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
			}
		};
		worker.execute();
	}

	protected void editSetting(final SettingModel settingModel) {
		final JTextField textField = new JTextField(settingModel.currentValue);
		final JPanel editPanel = new JPanel(new GridLayout(0, 1));
		editPanel.add(new JLabel("New Value"));
		editPanel.add(textField);

		final JPanel settingPanel = new JPanel(new BorderLayout());
		settingPanel.add(new SettingPanel(settingModel), BorderLayout.CENTER);
		settingPanel.add(editPanel, BorderLayout.SOUTH);
		settingPanel.setPreferredSize(new Dimension(800, 200));

		String[] options;
		if (settingModel.currentValue.equals(settingModel.defaultValue)) {
			options = new String[] { Translation.get("gb.cancel"), Translation.get("gb.save") };
		} else {
			options = new String[] { Translation.get("gb.cancel"),
					Translation.get("gb.setDefault"), Translation.get("gb.save") };
		}
		final String defaultOption = options[0];
		final int selection = JOptionPane.showOptionDialog(SettingsPanel.this, settingPanel,
				settingModel.name, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
				new ImageIcon(getClass().getResource("/settings_16x16.png")), options,
				defaultOption);
		if (selection <= 0) {
			return;
		}
		if (options[selection].equals(Translation.get("gb.setDefault"))) {
			textField.setText(settingModel.defaultValue);
		}
		final Map<String, String> newSettings = new HashMap<String, String>();
		newSettings.put(settingModel.name, textField.getText().trim());
		final GitblitWorker worker = new GitblitWorker(SettingsPanel.this, RpcRequest.EDIT_SETTINGS) {
			@Override
			protected Boolean doRequest() throws IOException {
				final boolean success = SettingsPanel.this.gitblit.updateSettings(newSettings);
				if (success) {
					SettingsPanel.this.gitblit.refreshSettings();
				}
				return success;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
			}
		};
		worker.execute();
	}
}
