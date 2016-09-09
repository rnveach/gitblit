/*
 * Copyright 2012 gitblit.com.
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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.PermissionType;
import com.gitblit.Constants.RegistrantType;
import com.gitblit.client.Utils.RowRenderer;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.utils.StringUtils;

public class RegistrantPermissionsPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private JTable permissionsTable;

	private RegistrantPermissionsTableModel tableModel;

	private DefaultComboBoxModel<String> registrantModel;

	private JComboBox<String> registrantSelector;

	private JComboBox<AccessPermission> permissionSelector;

	private JButton addButton;

	private JPanel addPanel;

	public RegistrantPermissionsPanel(final RegistrantType registrantType) {
		super(new BorderLayout(5, 5));
		this.tableModel = new RegistrantPermissionsTableModel();
		this.permissionsTable = Utils.newTable(this.tableModel, Utils.DATE_FORMAT,
				new RowRenderer() {
					Color clear = new Color(0, 0, 0, 0);
					Color iceGray = new Color(0xf0, 0xf0, 0xf0);

					@Override
					public void prepareRow(Component c, boolean isSelected, int row, int column) {
						if (isSelected) {
							c.setBackground(RegistrantPermissionsPanel.this.permissionsTable
									.getSelectionBackground());
						} else {
							if (RegistrantPermissionsPanel.this.tableModel.permissions.get(row).mutable) {
								c.setBackground(this.clear);
							} else {
								c.setBackground(this.iceGray);
							}
						}
					}
				});
		this.permissionsTable.setModel(this.tableModel);
		this.permissionsTable.setPreferredScrollableViewportSize(new Dimension(400, 150));
		final JScrollPane jsp = new JScrollPane(this.permissionsTable);
		add(jsp, BorderLayout.CENTER);

		this.permissionsTable.getColumnModel()
				.getColumn(RegistrantPermissionsTableModel.Columns.Registrant.ordinal())
				.setCellRenderer(new NameRenderer());
		this.permissionsTable.getColumnModel()
				.getColumn(RegistrantPermissionsTableModel.Columns.Type.ordinal())
				.setCellRenderer(new PermissionTypeRenderer());
		this.permissionsTable.getColumnModel()
				.getColumn(RegistrantPermissionsTableModel.Columns.Permission.ordinal())
				.setCellEditor(new AccessPermissionEditor());

		this.registrantModel = new DefaultComboBoxModel<String>();
		this.registrantSelector = new JComboBox<String>(this.registrantModel);
		this.permissionSelector = new JComboBox<AccessPermission>(AccessPermission.NEWPERMISSIONS);
		this.addButton = new JButton(Translation.get("gb.add"));
		this.addButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (RegistrantPermissionsPanel.this.registrantSelector.getSelectedIndex() < 0) {
					return;
				}
				if (RegistrantPermissionsPanel.this.permissionSelector.getSelectedIndex() < 0) {
					return;
				}

				final RegistrantAccessPermission rp = new RegistrantAccessPermission(registrantType);
				rp.registrant = RegistrantPermissionsPanel.this.registrantSelector
						.getSelectedItem().toString();
				rp.permission = (AccessPermission) RegistrantPermissionsPanel.this.permissionSelector
						.getSelectedItem();
				if (StringUtils.findInvalidCharacter(rp.registrant) != null) {
					rp.permissionType = PermissionType.REGEX;
					rp.source = rp.registrant;
				} else {
					rp.permissionType = PermissionType.EXPLICIT;
				}

				RegistrantPermissionsPanel.this.tableModel.permissions.add(rp);
				// resort permissions after insert to convey idea of eval order
				Collections.sort(RegistrantPermissionsPanel.this.tableModel.permissions);

				RegistrantPermissionsPanel.this.registrantModel.removeElement(rp.registrant);
				RegistrantPermissionsPanel.this.registrantSelector.setSelectedIndex(-1);
				RegistrantPermissionsPanel.this.registrantSelector.invalidate();
				RegistrantPermissionsPanel.this.addPanel
						.setVisible(RegistrantPermissionsPanel.this.registrantModel.getSize() > 0);

				RegistrantPermissionsPanel.this.tableModel.fireTableDataChanged();
			}
		});

		this.addPanel = new JPanel();
		this.addPanel.add(this.registrantSelector);
		this.addPanel.add(this.permissionSelector);
		this.addPanel.add(this.addButton);
		add(this.addPanel, BorderLayout.SOUTH);
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		this.permissionsTable.setEnabled(enabled);
		this.registrantSelector.setEnabled(enabled);
		this.permissionSelector.setEnabled(enabled);
		this.addButton.setEnabled(enabled);
	}

	public void setObjects(List<String> registrants, List<RegistrantAccessPermission> permissions) {
		List<String> filtered;
		if (registrants == null) {
			filtered = new ArrayList<String>();
		} else {
			filtered = new ArrayList<String>(registrants);
		}
		if (permissions == null) {
			permissions = new ArrayList<RegistrantAccessPermission>();
		}
		for (final RegistrantAccessPermission rp : permissions) {
			if (rp.mutable) {
				// only remove editable duplicates
				// this allows for specifying an explicit permission
				filtered.remove(rp.registrant);
			} else if (rp.isAdmin()) {
				// administrators can not have their permission changed
				filtered.remove(rp.registrant);
			} else if (rp.isOwner()) {
				// owners can not have their permission changed
				filtered.remove(rp.registrant);
			}
		}
		for (final String registrant : filtered) {
			this.registrantModel.addElement(registrant);
		}
		this.tableModel.setPermissions(permissions);

		this.registrantSelector.setSelectedIndex(-1);
		this.permissionSelector.setSelectedIndex(-1);
		this.addPanel.setVisible(filtered.size() > 0);
	}

	public List<RegistrantAccessPermission> getPermissions() {
		return this.tableModel.permissions;
	}

	private class AccessPermissionEditor extends DefaultCellEditor {

		private static final long serialVersionUID = 1L;

		public AccessPermissionEditor() {
			super(new JComboBox<AccessPermission>(AccessPermission.values()));
		}
	}

	private class PermissionTypeRenderer extends DefaultTableCellRenderer {

		private static final long serialVersionUID = 1L;

		public PermissionTypeRenderer() {
			super();
			setHorizontalAlignment(SwingConstants.CENTER);
		}

		@Override
		protected void setValue(Object value) {
			final RegistrantAccessPermission ap = (RegistrantAccessPermission) value;
			switch (ap.permissionType) {
			case ADMINISTRATOR:
				setText(ap.source == null ? Translation.get("gb.administrator") : ap.source);
				setToolTipText(Translation.get("gb.administratorPermission"));
				break;
			case OWNER:
				setText(Translation.get("gb.owner"));
				setToolTipText(Translation.get("gb.ownerPermission"));
				break;
			case TEAM:
				setText(ap.source == null ? Translation.get("gb.team") : ap.source);
				setToolTipText(MessageFormat
						.format(Translation.get("gb.teamPermission"), ap.source));
				break;
			case REGEX:
				setText("regex");
				setToolTipText(MessageFormat.format(Translation.get("gb.regexPermission"),
						ap.source));
				break;
			default:
				if (ap.isMissing()) {
					setText(Translation.get("gb.missing"));
					setToolTipText(Translation.get("gb.missingPermission"));
				} else {
					setText("");
					setToolTipText(null);
				}
				break;
			}
		}
	}
}
