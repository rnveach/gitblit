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
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import com.gitblit.Constants;
import com.gitblit.Constants.RpcRequest;
import com.gitblit.models.ServerStatus;
import com.gitblit.utils.ByteFormat;

/**
 * This panel displays the server status.
 *
 * @author James Moger
 */
public class StatusPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private final GitblitClient gitblit;
	private JLabel bootDate;
	private JLabel url;
	private JLabel servletContainer;
	private JLabel heapMaximum;
	private JLabel heapAllocated;
	private JLabel heapUsed;
	private PropertiesTableModel tableModel;
	private HeaderPanel header;
	private JLabel version;
	private JLabel releaseDate;

	public StatusPanel(GitblitClient gitblit) {
		super();
		this.gitblit = gitblit;
		initialize();
	}

	private void initialize() {
		final JButton refreshStatus = new JButton(Translation.get("gb.refresh"));
		refreshStatus.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				refreshStatus();
			}
		});

		this.version = new JLabel();
		this.releaseDate = new JLabel();
		this.bootDate = new JLabel();
		this.url = new JLabel();
		this.servletContainer = new JLabel();

		this.heapMaximum = new JLabel();
		this.heapAllocated = new JLabel();
		this.heapUsed = new JLabel();

		final JPanel fieldsPanel = new JPanel(new GridLayout(0, 1, 0, Utils.MARGIN)) {

			private static final long serialVersionUID = 1L;

			@Override
			public Insets getInsets() {
				return Utils.INSETS;
			}
		};
		fieldsPanel.add(createFieldPanel("gb.version", this.version));
		fieldsPanel.add(createFieldPanel("gb.releaseDate", this.releaseDate));
		fieldsPanel.add(createFieldPanel("gb.bootDate", this.bootDate));
		fieldsPanel.add(createFieldPanel("gb.url", this.url));
		fieldsPanel.add(createFieldPanel("gb.servletContainer", this.servletContainer));
		fieldsPanel.add(createFieldPanel("gb.heapUsed", this.heapUsed));
		fieldsPanel.add(createFieldPanel("gb.heapAllocated", this.heapAllocated));
		fieldsPanel.add(createFieldPanel("gb.heapMaximum", this.heapMaximum));

		this.tableModel = new PropertiesTableModel();
		final JTable propertiesTable = Utils.newTable(this.tableModel, Utils.DATE_FORMAT);
		final String name = propertiesTable.getColumnName(PropertiesTableModel.Columns.Name
				.ordinal());
		final NameRenderer nameRenderer = new NameRenderer();
		propertiesTable.getColumn(name).setCellRenderer(nameRenderer);

		final JPanel centerPanel = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		centerPanel.add(fieldsPanel, BorderLayout.NORTH);
		centerPanel.add(new JScrollPane(propertiesTable), BorderLayout.CENTER);

		final JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, Utils.MARGIN, 0));
		controls.add(refreshStatus);

		this.header = new HeaderPanel(Translation.get("gb.status"), "health_16x16.png");
		setLayout(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		add(this.header, BorderLayout.NORTH);
		add(centerPanel, BorderLayout.CENTER);
		add(controls, BorderLayout.SOUTH);
	}

	private static JPanel createFieldPanel(String key, JLabel valueLabel) {
		final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, Utils.MARGIN, 0));
		final JLabel textLabel = new JLabel(Translation.get(key));
		textLabel.setFont(textLabel.getFont().deriveFont(Font.BOLD));
		textLabel.setPreferredSize(new Dimension(120, 10));
		panel.add(textLabel);
		panel.add(valueLabel);
		return panel;
	}

	@Override
	public Insets getInsets() {
		return Utils.INSETS;
	}

	protected void refreshStatus() {
		final GitblitWorker worker = new GitblitWorker(StatusPanel.this, RpcRequest.LIST_STATUS) {
			@Override
			protected Boolean doRequest() throws IOException {
				StatusPanel.this.gitblit.refreshStatus();
				return true;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
			}
		};
		worker.execute();
	}

	protected void updateTable(boolean pack) {
		final ServerStatus status = this.gitblit.getStatus();
		this.header.setText(Translation.get("gb.status"));
		this.version.setText(Constants.NAME + (status.isGO ? " GO v" : " WAR v") + status.version);
		this.releaseDate.setText(status.releaseDate);
		this.bootDate.setText(status.bootDate.toString() + " ("
				+ Translation.getTimeUtils().timeAgo(status.bootDate) + ")");
		this.url.setText(this.gitblit.url);
		this.servletContainer.setText(status.servletContainer);
		final ByteFormat byteFormat = new ByteFormat();
		this.heapMaximum.setText(byteFormat.format(status.heapMaximum));
		this.heapAllocated.setText(byteFormat.format(status.heapAllocated));
		this.heapUsed.setText(byteFormat.format(status.heapAllocated - status.heapFree) + " ("
				+ byteFormat.format(status.heapFree) + " " + Translation.get("gb.free") + ")");
		this.tableModel.setProperties(status.systemProperties);
		this.tableModel.fireTableDataChanged();
	}
}
