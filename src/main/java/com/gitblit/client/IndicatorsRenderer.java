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

import java.awt.Component;
import java.awt.FlowLayout;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

import com.gitblit.models.RepositoryModel;

/**
 * Renders the type indicators (tickets, frozen, access restriction, etc) in a
 * single cell.
 *
 * @author James Moger
 *
 */
public class IndicatorsRenderer extends JPanel implements TableCellRenderer {

	private static final long serialVersionUID = 1L;

	private final ImageIcon blankIcon;

	private final ImageIcon pushIcon;

	private final ImageIcon pullIcon;

	private final ImageIcon viewIcon;

	private final ImageIcon frozenIcon;

	private final ImageIcon federatedIcon;

	private final ImageIcon forkIcon;

	private final ImageIcon sparkleshareIcon;

	private final ImageIcon mirrorIcon;

	public IndicatorsRenderer() {
		super(new FlowLayout(FlowLayout.RIGHT, 1, 0));
		this.blankIcon = new ImageIcon(getClass().getResource("/blank.png"));
		this.pushIcon = new ImageIcon(getClass().getResource("/lock_go_16x16.png"));
		this.pullIcon = new ImageIcon(getClass().getResource("/lock_pull_16x16.png"));
		this.viewIcon = new ImageIcon(getClass().getResource("/shield_16x16.png"));
		this.frozenIcon = new ImageIcon(getClass().getResource("/cold_16x16.png"));
		this.federatedIcon = new ImageIcon(getClass().getResource("/federated_16x16.png"));
		this.forkIcon = new ImageIcon(getClass().getResource("/commit_divide_16x16.png"));
		this.sparkleshareIcon = new ImageIcon(getClass().getResource("/star_16x16.png"));
		this.mirrorIcon = new ImageIcon(getClass().getResource("/mirror_16x16.png"));
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {
		if (isSelected) {
			setBackground(table.getSelectionBackground());
		} else {
			setBackground(table.getBackground());
		}
		removeAll();
		if (value instanceof RepositoryModel) {
			final StringBuilder tooltip = new StringBuilder();
			final RepositoryModel model = (RepositoryModel) value;
			if (model.isSparkleshared()) {
				final JLabel icon = new JLabel(this.sparkleshareIcon);
				tooltip.append(Translation.get("gb.isSparkleshared")).append("<br/>");
				add(icon);
			}
			if (model.isMirror) {
				final JLabel icon = new JLabel(this.mirrorIcon);
				tooltip.append(Translation.get("gb.isMirror")).append("<br/>");
				add(icon);
			}
			if (model.isFork()) {
				final JLabel icon = new JLabel(this.forkIcon);
				tooltip.append(Translation.get("gb.isFork")).append("<br/>");
				add(icon);
			}
			if (model.isFrozen) {
				final JLabel icon = new JLabel(this.frozenIcon);
				tooltip.append(Translation.get("gb.isFrozen")).append("<br/>");
				add(icon);
			}
			if (model.isFederated) {
				final JLabel icon = new JLabel(this.federatedIcon);
				tooltip.append(Translation.get("gb.isFederated")).append("<br/>");
				add(icon);
			}

			switch (model.accessRestriction) {
			case NONE: {
				add(new JLabel(this.blankIcon));
				break;
			}
			case PUSH: {
				final JLabel icon = new JLabel(this.pushIcon);
				tooltip.append(Translation.get("gb.pushRestricted")).append("<br/>");
				add(icon);
				break;
			}
			case CLONE: {
				final JLabel icon = new JLabel(this.pullIcon);
				tooltip.append(Translation.get("gb.cloneRestricted")).append("<br/>");
				add(icon);
				break;
			}
			case VIEW: {
				final JLabel icon = new JLabel(this.viewIcon);
				tooltip.append(Translation.get("gb.viewRestricted")).append("<br/>");
				add(icon);
				break;
			}
			default:
				add(new JLabel(this.blankIcon));
			}
			if (tooltip.length() > 0) {
				tooltip.insert(0, "<html><body>");
				setToolTipText(tooltip.toString().trim());
			}
		}
		return this;
	}
}