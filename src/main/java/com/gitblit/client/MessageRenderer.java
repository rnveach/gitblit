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

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.table.TableCellRenderer;

import org.eclipse.jgit.lib.Constants;

import com.gitblit.models.FeedEntryModel;

/**
 * Message renderer displays the short log message and then any refs in a style
 * like the site.
 *
 * @author James Moger
 *
 */
public class MessageRenderer extends JPanel implements TableCellRenderer {

	private static final long serialVersionUID = 1L;

	private final GitblitClient gitblit;

	private final ImageIcon mergeIcon;

	private final ImageIcon blankIcon;

	private final JLabel messageLabel;

	private final JLabel headLabel;

	private final JLabel branchLabel;

	private final JLabel remoteLabel;

	private final JLabel tagLabel;

	public MessageRenderer() {
		this(null);
	}

	public MessageRenderer(GitblitClient gitblit) {
		super(new FlowLayout(FlowLayout.LEFT, Utils.MARGIN, 1));
		this.gitblit = gitblit;

		this.mergeIcon = new ImageIcon(getClass().getResource("/commit_merge_16x16.png"));
		this.blankIcon = new ImageIcon(getClass().getResource("/blank.png"));

		this.messageLabel = new JLabel();

		this.headLabel = newRefLabel();
		this.branchLabel = newRefLabel();
		this.remoteLabel = newRefLabel();
		this.tagLabel = newRefLabel();

		add(this.messageLabel);
		add(this.headLabel);
		add(this.branchLabel);
		add(this.remoteLabel);
		add(this.tagLabel);
	}

	private static JLabel newRefLabel() {
		final JLabel label = new JLabel();
		label.setOpaque(true);
		final Font font = label.getFont();
		label.setFont(font.deriveFont(font.getSize2D() - 1f));
		return label;
	}

	private void resetRef(JLabel label) {
		label.setText("");
		label.setBackground(this.messageLabel.getBackground());
		label.setBorder(null);
		label.setVisible(false);
	}

	private void showRef(String ref, JLabel label) {
		String name = ref;
		Color bg = getBackground();
		Border border = null;
		if (name.startsWith(Constants.R_HEADS)) {
			// local branch
			bg = Color.decode("#CCFFCC");
			name = name.substring(Constants.R_HEADS.length());
			border = new LineBorder(Color.decode("#00CC33"), 1);
		} else if (name.startsWith(Constants.R_REMOTES)) {
			// remote branch
			bg = Color.decode("#CAC2F5");
			name = name.substring(Constants.R_REMOTES.length());
			border = new LineBorder(Color.decode("#6C6CBF"), 1);
		} else if (name.startsWith(Constants.R_TAGS)) {
			// tag
			bg = Color.decode("#FFFFAA");
			name = name.substring(Constants.R_TAGS.length());
			border = new LineBorder(Color.decode("#FFCC00"), 1);
		} else if (name.equals(Constants.HEAD)) {
			// HEAD
			bg = Color.decode("#FFAAFF");
			border = new LineBorder(Color.decode("#FF00EE"), 1);
		} else {
		}
		label.setText(name);
		label.setBackground(bg);
		label.setBorder(border);
		label.setVisible(true);
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {
		if (isSelected) {
			setBackground(table.getSelectionBackground());
		} else {
			setBackground(table.getBackground());
		}
		this.messageLabel
				.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
		if (value == null) {
			return this;
		}
		final FeedEntryModel entry = (FeedEntryModel) value;

		if (this.gitblit == null) {
			// no gitblit client, just display message
			this.messageLabel.setText(entry.title);
		} else {
			// show message in BOLD if its a new entry
			if (entry.published
					.after(this.gitblit.getLastFeedRefresh(entry.repository, entry.branch))) {
				this.messageLabel.setText("<html><body><b>" + entry.title);
			} else {
				this.messageLabel.setText(entry.title);
			}
		}

		// reset ref label
		resetRef(this.headLabel);
		resetRef(this.branchLabel);
		resetRef(this.remoteLabel);
		resetRef(this.tagLabel);

		int parentCount = 0;
		if (entry.tags != null) {
			for (String tag : entry.tags) {
				if (tag.startsWith("ref:")) {
					// strip ref:
					tag = tag.substring("ref:".length());
				} else {
					// count parents
					if (tag.startsWith("parent:")) {
						parentCount++;
					}
				}
				if (tag.equals(entry.branch)) {
					// skip current branch label
					continue;
				}
				if (tag.startsWith(Constants.R_HEADS)) {
					// local branch
					showRef(tag, this.branchLabel);
				} else if (tag.startsWith(Constants.R_REMOTES)) {
					// remote branch
					showRef(tag, this.remoteLabel);
				} else if (tag.startsWith(Constants.R_TAGS)) {
					// tag
					showRef(tag, this.tagLabel);
				} else if (tag.equals(Constants.HEAD)) {
					// HEAD
					showRef(tag, this.headLabel);
				}
			}
		}

		if (parentCount > 1) {
			// multiple parents, show merge icon
			this.messageLabel.setIcon(this.mergeIcon);
		} else {
			this.messageLabel.setIcon(this.blankIcon);
		}
		return this;
	}
}