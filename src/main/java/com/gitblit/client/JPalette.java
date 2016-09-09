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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

public class JPalette<T> extends JPanel {

	private static final long serialVersionUID = 1L;
	private PaletteModel<T> availableModel;
	private PaletteModel<T> selectedModel;
	private JButton add;
	private JButton subtract;
	private JButton up;
	private JButton down;

	public JPalette() {
		this(false);
	}

	public JPalette(boolean controlOrder) {
		super(new BorderLayout(5, 5));

		this.availableModel = new PaletteModel<T>();
		this.selectedModel = new PaletteModel<T>();

		final JTable available = new JTable(this.availableModel);
		final JTable selected = new JTable(this.selectedModel);

		this.add = new JButton("->");
		this.add.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				final List<T> move = new ArrayList<T>();
				if (available.getSelectedRowCount() <= 0) {
					return;
				}
				for (final int row : available.getSelectedRows()) {
					final int modelIndex = available.convertRowIndexToModel(row);
					final T item = JPalette.this.availableModel.list.get(modelIndex);
					move.add(item);
				}
				JPalette.this.availableModel.list.removeAll(move);
				JPalette.this.selectedModel.list.addAll(move);
				JPalette.this.availableModel.fireTableDataChanged();
				JPalette.this.selectedModel.fireTableDataChanged();
			}
		});
		this.subtract = new JButton("<-");
		this.subtract.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				final List<T> move = new ArrayList<T>();
				if (selected.getSelectedRowCount() <= 0) {
					return;
				}
				for (final int row : selected.getSelectedRows()) {
					final int modelIndex = selected.convertRowIndexToModel(row);
					final T item = JPalette.this.selectedModel.list.get(modelIndex);
					move.add(item);
				}
				JPalette.this.selectedModel.list.removeAll(move);
				JPalette.this.availableModel.list.addAll(move);

				JPalette.this.selectedModel.fireTableDataChanged();
				JPalette.this.availableModel.fireTableDataChanged();
			}
		});

		this.up = new JButton("\u2191");
		this.up.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				final int row = selected.getSelectedRow();
				if (row > 0) {
					final T o = JPalette.this.selectedModel.list.remove(row);
					JPalette.this.selectedModel.list.add(row - 1, o);
					JPalette.this.selectedModel.fireTableDataChanged();
				}
			}
		});

		this.down = new JButton("\u2193");
		this.down.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				final int row = selected.getSelectedRow();
				if (row < (selected.getRowCount() - 1)) {
					final T o = JPalette.this.selectedModel.list.remove(row);
					JPalette.this.selectedModel.list.add(row + 1, o);
					JPalette.this.selectedModel.fireTableDataChanged();
				}
			}
		});

		final JPanel controls = new JPanel(new GridLayout(0, 1, 0, 5));
		controls.add(this.add);
		controls.add(this.subtract);
		if (controlOrder) {
			controls.add(this.up);
			controls.add(this.down);
		}

		final JPanel center = new JPanel(new GridBagLayout());
		center.add(controls);

		add(newListPanel(Translation.get("gb.available"), available), BorderLayout.WEST);
		add(center, BorderLayout.CENTER);
		add(newListPanel(Translation.get("gb.selected"), selected), BorderLayout.EAST);
	}

	private static JPanel newListPanel(String label, JTable table) {
		final NameRenderer nameRenderer = new NameRenderer();
		table.setCellSelectionEnabled(false);
		table.setRowSelectionAllowed(true);
		table.getTableHeader().setReorderingAllowed(false);
		table.setGridColor(new Color(0xd9d9d9));
		table.setBackground(Color.white);
		table.getColumn(table.getColumnName(0)).setCellRenderer(nameRenderer);

		final JScrollPane jsp = new JScrollPane(table);
		jsp.setPreferredSize(new Dimension(225, 160));
		final JPanel panel = new JPanel(new BorderLayout());
		final JLabel jlabel = new JLabel(label);
		jlabel.setFont(jlabel.getFont().deriveFont(Font.BOLD));
		panel.add(jlabel, BorderLayout.NORTH);
		panel.add(jsp, BorderLayout.CENTER);
		return panel;
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		this.add.setEnabled(enabled);
		this.subtract.setEnabled(enabled);
		this.up.setEnabled(enabled);
		this.down.setEnabled(enabled);
	}

	public void setObjects(List<T> all, List<T> selected) {
		final List<T> available = new ArrayList<T>(all);
		if (selected != null) {
			available.removeAll(selected);
		}
		this.availableModel.list.clear();
		this.availableModel.list.addAll(available);
		this.availableModel.fireTableDataChanged();

		if (selected != null) {
			this.selectedModel.list.clear();
			this.selectedModel.list.addAll(selected);
			this.selectedModel.fireTableDataChanged();
		}
	}

	public List<T> getSelections() {
		return new ArrayList<T>(this.selectedModel.list);
	}

	public class PaletteModel<K> extends AbstractTableModel {

		private static final long serialVersionUID = 1L;

		List<K> list;

		public PaletteModel() {
			this(new ArrayList<K>());
		}

		public PaletteModel(List<K> list) {
			this.list = new ArrayList<K>(list);
		}

		@Override
		public int getRowCount() {
			return this.list.size();
		}

		@Override
		public int getColumnCount() {
			return 1;
		}

		@Override
		public String getColumnName(int column) {
			return Translation.get("gb.name");
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			return String.class;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			final K o = this.list.get(rowIndex);
			return o.toString();
		}
	}
}
