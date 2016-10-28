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
package com.gitblit.authority;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Date;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.gitblit.client.HeaderPanel;
import com.gitblit.client.Translation;
import com.gitblit.utils.StringUtils;
import com.toedter.calendar.JDateChooser;

public class NewSSLCertificateDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	JDateChooser expirationDate;
	JTextField hostname;
	JCheckBox serveCertificate;
	boolean isCanceled = true;

	public NewSSLCertificateDialog(Frame owner, Date defaultExpiration) {
		super(owner);

		setTitle(Translation.get("gb.newSSLCertificate"));

		final JPanel content = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN)) {
			private static final long serialVersionUID = 1L;

			@Override
			public Insets getInsets() {

				return Utils.INSETS;
			}
		};

		this.expirationDate = new JDateChooser(defaultExpiration);
		this.hostname = new JTextField(20);
		this.serveCertificate = new JCheckBox(Translation.get("gb.serveCertificate"), true);

		final JPanel panel = new JPanel(new GridLayout(0, 2, Utils.MARGIN, Utils.MARGIN));

		panel.add(new JLabel(Translation.get("gb.hostname")));
		panel.add(this.hostname);

		panel.add(new JLabel(Translation.get("gb.expires")));
		panel.add(this.expirationDate);

		panel.add(new JLabel(""));
		panel.add(this.serveCertificate);

		final JButton ok = new JButton(Translation.get("gb.ok"));
		ok.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (validateInputs()) {
					NewSSLCertificateDialog.this.isCanceled = false;
					setVisible(false);
				}
			}
		});
		final JButton cancel = new JButton(Translation.get("gb.cancel"));
		cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				NewSSLCertificateDialog.this.isCanceled = true;
				setVisible(false);
			}
		});

		final JPanel controls = new JPanel();
		controls.add(ok);
		controls.add(cancel);

		content.add(panel, BorderLayout.CENTER);
		content.add(controls, BorderLayout.SOUTH);

		getContentPane().add(
				new HeaderPanel(Translation.get("gb.newSSLCertificate"), "rosette_16x16.png"),
				BorderLayout.NORTH);
		getContentPane().add(content, BorderLayout.CENTER);
		pack();

		setLocationRelativeTo(owner);
	}

	private boolean validateInputs() {
		if (getExpiration().getTime() < System.currentTimeMillis()) {
			// expires before now
			JOptionPane.showMessageDialog(this, Translation.get("gb.invalidExpirationDate"),
					Translation.get("gb.error"), JOptionPane.ERROR_MESSAGE);
			return false;
		}
		if (StringUtils.isEmpty(getHostname())) {
			// must have hostname
			JOptionPane.showMessageDialog(this, Translation.get("gb.hostnameRequired"),
					Translation.get("gb.error"), JOptionPane.ERROR_MESSAGE);
			return false;
		}
		return true;
	}

	public String getHostname() {
		return this.hostname.getText();
	}

	public Date getExpiration() {
		return this.expirationDate.getDate();
	}

	public boolean isServeCertificate() {
		return this.serveCertificate.isSelected();
	}

	public boolean isCanceled() {
		return this.isCanceled;
	}
}
