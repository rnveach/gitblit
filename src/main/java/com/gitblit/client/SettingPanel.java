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
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

import com.gitblit.models.SettingModel;
import com.gitblit.utils.StringUtils;

/**
 * This panel displays the metadata for a particular setting.
 *
 * @author James Moger
 */
public class SettingPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private JTextArea descriptionArea;
	private JLabel settingName;
	private JLabel settingDefault;
	private JLabel sinceVersion;
	private JLabel directives;

	public SettingPanel() {
		super();
		initialize();
	}

	public SettingPanel(SettingModel setting) {
		this();
		setSetting(setting);
	}

	private void initialize() {
		this.descriptionArea = new JTextArea();
		this.descriptionArea.setRows(6);
		this.descriptionArea.setFont(new Font("monospaced", Font.PLAIN, 11));
		this.descriptionArea.setEditable(false);

		this.settingName = new JLabel(" ");
		this.settingName.setFont(this.settingName.getFont().deriveFont(Font.BOLD));

		this.settingDefault = new JLabel(" ");

		this.sinceVersion = new JLabel(" ", SwingConstants.RIGHT);
		this.sinceVersion.setForeground(new Color(0, 0x80, 0));

		this.directives = new JLabel(" ", SwingConstants.RIGHT);
		this.directives.setFont(this.directives.getFont().deriveFont(Font.ITALIC));

		final JPanel settingParameters = new JPanel(new GridLayout(2, 2, 0, 0));
		settingParameters.add(this.settingName);
		settingParameters.add(this.sinceVersion);
		settingParameters.add(this.settingDefault, BorderLayout.CENTER);
		settingParameters.add(this.directives);

		final JPanel settingPanel = new JPanel(new BorderLayout(5, 5));
		settingPanel.add(settingParameters, BorderLayout.NORTH);
		settingPanel.add(new JScrollPane(this.descriptionArea), BorderLayout.CENTER);
		setLayout(new BorderLayout(0, 0));
		add(settingPanel, BorderLayout.CENTER);
	}

	public void setSetting(SettingModel setting) {
		this.settingName.setText(setting.name);
		if (setting.since == null) {
			this.sinceVersion.setText("custom");
		} else {
			this.sinceVersion.setText("since " + setting.since);
		}
		this.settingDefault.setText(Translation.get("gb.default") + ": " + setting.defaultValue);

		final List<String> values = new ArrayList<String>();
		if (setting.caseSensitive) {
			values.add("CASE-SENSITIVE");
		}
		if (setting.spaceDelimited) {
			values.add("SPACE-DELIMITED");
		}
		if (setting.restartRequired) {
			values.add("RESTART REQUIRED");
		}
		this.directives.setText(StringUtils.flattenStrings(values, ", "));

		this.descriptionArea.setText(setting.description);
		this.descriptionArea.setCaretPosition(0);
	}

	public void clear() {
		this.settingName.setText(" ");
		this.settingDefault.setText(" ");
		this.sinceVersion.setText(" ");
		this.directives.setText(" ");
		this.descriptionArea.setText("");
	}
}
