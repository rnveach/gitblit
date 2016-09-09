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
import java.awt.Dimension;
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
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import org.bouncycastle.util.Arrays;

import com.gitblit.client.HeaderPanel;
import com.gitblit.client.Translation;
import com.gitblit.utils.StringUtils;
import com.toedter.calendar.JDateChooser;

public class NewClientCertificateDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	JDateChooser expirationDate;
	JPasswordField pw1;
	JPasswordField pw2;
	JTextField hint;
	JCheckBox sendEmail;
	boolean isCanceled = true;

	public NewClientCertificateDialog(Frame owner, String displayname, Date defaultExpiration,
			boolean allowEmail) {
		super(owner);

		setTitle(Translation.get("gb.newCertificate"));

		final JPanel content = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN)) {
			private static final long serialVersionUID = 1L;

			@Override
			public Insets getInsets() {

				return Utils.INSETS;
			}
		};

		this.expirationDate = new JDateChooser(defaultExpiration);
		this.pw1 = new JPasswordField(20);
		this.pw2 = new JPasswordField(20);
		this.hint = new JTextField(20);
		this.sendEmail = new JCheckBox(Translation.get("gb.sendEmail"));

		final JPanel panel = new JPanel(new GridLayout(0, 2, Utils.MARGIN, Utils.MARGIN));

		panel.add(new JLabel(Translation.get("gb.expires")));
		panel.add(this.expirationDate);

		panel.add(new JLabel(Translation.get("gb.password")));
		panel.add(this.pw1);

		panel.add(new JLabel(Translation.get("gb.confirmPassword")));
		panel.add(this.pw2);

		panel.add(new JLabel(Translation.get("gb.passwordHint")));
		panel.add(this.hint);

		if (allowEmail) {
			panel.add(new JLabel(""));
			panel.add(this.sendEmail);
		}

		final JButton ok = new JButton(Translation.get("gb.ok"));
		ok.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (validateInputs()) {
					NewClientCertificateDialog.this.isCanceled = false;
					setVisible(false);
				}
			}
		});
		final JButton cancel = new JButton(Translation.get("gb.cancel"));
		cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				NewClientCertificateDialog.this.isCanceled = true;
				setVisible(false);
			}
		});

		final JPanel controls = new JPanel();
		controls.add(ok);
		controls.add(cancel);

		final JTextArea message = new JTextArea(Translation.get("gb.newClientCertificateMessage"));
		message.setLineWrap(true);
		message.setWrapStyleWord(true);
		message.setEditable(false);
		message.setRows(6);
		message.setPreferredSize(new Dimension(300, 100));

		content.add(new JScrollPane(message), BorderLayout.CENTER);
		content.add(panel, BorderLayout.NORTH);
		content.add(controls, BorderLayout.SOUTH);

		getContentPane().add(
				new HeaderPanel(Translation.get("gb.newCertificate") + " (" + displayname + ")",
						"rosette_16x16.png"), BorderLayout.NORTH);
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
		if ((this.pw1.getPassword().length == 0)
				|| !Arrays.areEqual(this.pw1.getPassword(), this.pw2.getPassword())) {
			// password mismatch
			JOptionPane.showMessageDialog(this, Translation.get("gb.passwordsDoNotMatch"),
					Translation.get("gb.error"), JOptionPane.ERROR_MESSAGE);
			return false;
		}
		if (StringUtils.isEmpty(getPasswordHint())) {
			// must have hint
			JOptionPane.showMessageDialog(this, Translation.get("gb.passwordHintRequired"),
					Translation.get("gb.error"), JOptionPane.ERROR_MESSAGE);
			return false;
		}
		return true;
	}

	public String getPassword() {
		return new String(this.pw1.getPassword());
	}

	public String getPasswordHint() {
		return this.hint.getText();
	}

	public Date getExpiration() {
		return this.expirationDate.getDate();
	}

	public boolean sendEmail() {
		return this.sendEmail.isSelected();
	}

	public boolean isCanceled() {
		return this.isCanceled;
	}
}
