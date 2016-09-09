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
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.gitblit.models.FeedModel;

/**
 * Displays a list of repository branches and allows the user to check or
 * uncheck branches.
 *
 * @author James Moger
 *
 */
public abstract class SubscriptionsDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final List<FeedModel> feeds;

	private JTable feedsTable;

	private FeedsTableModel model;

	public SubscriptionsDialog(List<FeedModel> registrations) {
		super();
		this.feeds = registrations;
		setTitle(Translation.get("gb.subscribe"));
		setIconImage(new ImageIcon(getClass().getResource("/gitblt-favicon.png")).getImage());
		initialize();
		setSize(600, 400);
	}

	@Override
	protected JRootPane createRootPane() {
		final KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
		final JRootPane rootPane = new JRootPane();
		rootPane.registerKeyboardAction(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				setVisible(false);
			}
		}, stroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
		return rootPane;
	}

	private void initialize() {
		final NameRenderer nameRenderer = new NameRenderer();
		this.model = new FeedsTableModel(this.feeds);
		this.feedsTable = Utils.newTable(this.model, Utils.DATE_FORMAT);
		this.feedsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) {
					return;
				}
				final int viewRow = SubscriptionsDialog.this.feedsTable.getSelectedRow();
				if (viewRow == -1) {
					return;
				}
				final int modelRow = SubscriptionsDialog.this.feedsTable
						.convertRowIndexToModel(viewRow);
				final FeedModel feed = SubscriptionsDialog.this.model.get(modelRow);
				feed.subscribed = !feed.subscribed;
				SubscriptionsDialog.this.model.fireTableDataChanged();
			}
		});

		final String repository = this.feedsTable.getColumnName(FeedsTableModel.Columns.Repository
				.ordinal());
		this.feedsTable.getColumn(repository).setCellRenderer(nameRenderer);

		final String branch = this.feedsTable.getColumnName(FeedsTableModel.Columns.Branch
				.ordinal());
		this.feedsTable.getColumn(branch).setCellRenderer(new BranchRenderer<String>());

		final String subscribed = this.feedsTable.getColumnName(FeedsTableModel.Columns.Subscribed
				.ordinal());
		this.feedsTable.getColumn(subscribed).setCellRenderer(new BooleanCellRenderer());
		this.feedsTable.getColumn(subscribed).setMinWidth(30);
		this.feedsTable.getColumn(subscribed).setMaxWidth(30);

		Utils.packColumns(this.feedsTable, 5);

		final JButton cancel = new JButton(Translation.get("gb.cancel"));
		cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				setVisible(false);
			}
		});

		final JButton save = new JButton(Translation.get("gb.save"));
		save.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				save();
			}
		});

		this.feedsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) {
					return;
				}
			}
		});

		final JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
		controls.add(cancel);
		controls.add(save);

		final Insets insets = new Insets(5, 5, 5, 5);
		final JPanel centerPanel = new JPanel(new BorderLayout(5, 5)) {

			private static final long serialVersionUID = 1L;

			@Override
			public Insets getInsets() {
				return insets;
			}
		};
		centerPanel.add(new HeaderPanel(Translation.get("gb.subscribe") + "...", "feed_16x16.png"),
				BorderLayout.NORTH);
		centerPanel.add(new JScrollPane(this.feedsTable), BorderLayout.CENTER);
		centerPanel.add(controls, BorderLayout.SOUTH);

		getContentPane().setLayout(new BorderLayout(5, 5));
		getContentPane().add(centerPanel, BorderLayout.CENTER);
	}

	public abstract void save();
}
