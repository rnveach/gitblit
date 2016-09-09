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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import com.gitblit.utils.StringUtils;

public class HeaderPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final Insets insets = new Insets(5, 5, 5, 5);

	private final Color lightColor = new Color(0, 0, 0x60);

	private final JLabel headerLabel;

	private final JLabel refreshLabel;

	public HeaderPanel(String text, String icon) {
		// super(new FlowLayout(FlowLayout.LEFT), true);
		super(new GridLayout(1, 2, 5, 5), true);
		setOpaque(true);
		setBackground(new Color(0, 0, 0x20));

		this.headerLabel = new JLabel(text);
		if (!StringUtils.isEmpty(icon)) {
			this.headerLabel.setIcon(new ImageIcon(getClass().getResource("/" + icon)));
		}
		this.headerLabel.setForeground(Color.white);
		this.headerLabel.setFont(this.headerLabel.getFont().deriveFont(14f));
		add(this.headerLabel);

		this.refreshLabel = new JLabel("", SwingConstants.RIGHT);
		this.refreshLabel.setForeground(Color.white);
		add(this.refreshLabel);
	}

	public void setText(String text) {
		this.headerLabel.setText(text);
		final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		this.refreshLabel.setText("refreshed " + df.format(new Date()));
	}

	@Override
	public Insets getInsets() {
		return this.insets;
	}

	@Override
	public void paintComponent(Graphics oldG) {
		final Graphics2D g = (Graphics2D) oldG;
		final Point2D startPoint = new Point2D.Float(0, 0);
		final Point2D endPoint = new Point2D.Float(0, getHeight());
		final Paint gradientPaint = new GradientPaint(startPoint, this.lightColor, endPoint,
				getBackground(), false);
		g.setPaint(gradientPaint);
		g.fill(new Rectangle2D.Double(0, 0, getWidth(), getHeight()));
		g.setColor(new Color(0xff, 0x99, 0x00));
		final int stroke = 2;
		g.setStroke(new BasicStroke(stroke));
		g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
	}
}
