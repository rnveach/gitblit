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
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.Date;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableRowSorter;

import com.gitblit.client.HeaderPanel;
import com.gitblit.client.Translation;
import com.gitblit.models.UserModel;
import com.gitblit.utils.X509Utils.RevocationReason;
import com.gitblit.utils.X509Utils.X509Metadata;

public abstract class UserCertificatePanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private Frame owner;

	private UserCertificateModel ucm;

	private UserOidsPanel oidsPanel;

	private CertificatesTableModel tableModel;

	private JButton saveUserButton;

	private JButton editUserButton;

	private JButton newCertificateButton;

	private JButton revokeCertificateButton;

	private JTable table;

	public UserCertificatePanel(Frame owner) {
		super(new BorderLayout());

		this.owner = owner;
		this.oidsPanel = new UserOidsPanel();

		final JPanel fp = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		fp.add(this.oidsPanel, BorderLayout.NORTH);

		final JPanel fieldsPanel = new JPanel(new BorderLayout());
		fieldsPanel.add(new HeaderPanel(Translation.get("gb.properties"), "vcard_16x16.png"),
				BorderLayout.NORTH);
		fieldsPanel.add(fp, BorderLayout.CENTER);

		this.saveUserButton = new JButton(Translation.get("gb.save"));
		this.saveUserButton.setEnabled(false);
		this.saveUserButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				setEditable(false);
				final String username = UserCertificatePanel.this.ucm.user.username;
				UserCertificatePanel.this.oidsPanel.updateUser(UserCertificatePanel.this.ucm);
				saveUser(username, UserCertificatePanel.this.ucm);
			}
		});

		this.editUserButton = new JButton(Translation.get("gb.edit"));
		this.editUserButton.setEnabled(false);
		this.editUserButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				setEditable(true);
			}
		});

		final JPanel userControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		userControls.add(this.editUserButton);
		userControls.add(this.saveUserButton);
		fieldsPanel.add(userControls, BorderLayout.SOUTH);

		final JPanel certificatesPanel = new JPanel(new BorderLayout());
		certificatesPanel.add(new HeaderPanel(Translation.get("gb.certificates"),
				"rosette_16x16.png"), BorderLayout.NORTH);
		this.tableModel = new CertificatesTableModel();
		this.table = Utils.newTable(this.tableModel, Utils.DATE_FORMAT);
		this.table.setRowSorter(new TableRowSorter<CertificatesTableModel>(this.tableModel));
		this.table.setDefaultRenderer(CertificateStatus.class, new CertificateStatusRenderer());
		this.table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) {
					return;
				}
				boolean enable = false;
				final int row = UserCertificatePanel.this.table.getSelectedRow();
				if (row > -1) {
					final int modelIndex = UserCertificatePanel.this.table
							.convertRowIndexToModel(row);
					final X509Certificate cert = UserCertificatePanel.this.tableModel
							.get(modelIndex);
					enable = !UserCertificatePanel.this.ucm.isRevoked(cert.getSerialNumber());
				}
				UserCertificatePanel.this.revokeCertificateButton.setEnabled(enable);
			}
		});
		this.table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					final int row = UserCertificatePanel.this.table.rowAtPoint(e.getPoint());
					final int modelIndex = UserCertificatePanel.this.table
							.convertRowIndexToModel(row);
					final X509Certificate cert = UserCertificatePanel.this.tableModel
							.get(modelIndex);
					final X509CertificateViewer viewer = new X509CertificateViewer(
							UserCertificatePanel.this.owner, cert);
					viewer.setVisible(true);
				}
			}
		});
		certificatesPanel.add(new JScrollPane(this.table), BorderLayout.CENTER);

		this.newCertificateButton = new JButton(Translation.get("gb.newCertificate"));
		this.newCertificateButton.setEnabled(false);
		this.newCertificateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					if (UserCertificatePanel.this.saveUserButton.isEnabled()) {
						// save changes
						final String username = UserCertificatePanel.this.ucm.user.username;
						setEditable(false);
						UserCertificatePanel.this.oidsPanel
								.updateUser(UserCertificatePanel.this.ucm);
						saveUser(username, UserCertificatePanel.this.ucm);
					}

					final NewClientCertificateDialog dialog = new NewClientCertificateDialog(
							UserCertificatePanel.this.owner, UserCertificatePanel.this.ucm.user
									.getDisplayName(), getDefaultExpiration(), isAllowEmail());
					dialog.setModal(true);
					dialog.setVisible(true);
					if (dialog.isCanceled()) {
						return;
					}

					final boolean sendEmail = dialog.sendEmail();
					final UserModel user = UserCertificatePanel.this.ucm.user;
					final X509Metadata metadata = new X509Metadata(user.username, dialog
							.getPassword());
					metadata.userDisplayname = user.getDisplayName();
					metadata.emailAddress = user.emailAddress;
					metadata.passwordHint = dialog.getPasswordHint();
					metadata.notAfter = dialog.getExpiration();

					final AuthorityWorker worker = new AuthorityWorker(
							UserCertificatePanel.this.owner) {
						@Override
						protected Boolean doRequest() throws IOException {
							return newCertificate(UserCertificatePanel.this.ucm, metadata,
									sendEmail);
						}

						@Override
						protected void onSuccess() {
							JOptionPane.showMessageDialog(
									UserCertificatePanel.this.owner,
									MessageFormat.format(
											Translation.get("gb.clientCertificateGenerated"),
											user.getDisplayName()),
									Translation.get("gb.newCertificate"),
									JOptionPane.INFORMATION_MESSAGE);
						}
					};
					worker.execute();
				}
				catch (final Exception x) {
					Utils.showException(UserCertificatePanel.this, x);
				}
			}
		});

		this.revokeCertificateButton = new JButton(Translation.get("gb.revokeCertificate"));
		this.revokeCertificateButton.setEnabled(false);
		this.revokeCertificateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					final int row = UserCertificatePanel.this.table.getSelectedRow();
					if (row < 0) {
						return;
					}
					final int modelIndex = UserCertificatePanel.this.table
							.convertRowIndexToModel(row);
					final X509Certificate cert = UserCertificatePanel.this.tableModel
							.get(modelIndex);

					final String[] choices = new String[RevocationReason.reasons.length];
					for (int i = 0; i < choices.length; i++) {
						choices[i] = Translation.get("gb." + RevocationReason.reasons[i].name());
					}

					final Object choice = JOptionPane.showInputDialog(
							UserCertificatePanel.this.owner,
							Translation.get("gb.revokeCertificateReason"),
							Translation.get("gb.revokeCertificate"), JOptionPane.PLAIN_MESSAGE,
							new ImageIcon(getClass().getResource("/rosette_32x32.png")), choices,
							Translation.get("gb.unspecified"));
					if (choice == null) {
						return;
					}
					RevocationReason selection = RevocationReason.unspecified;
					for (int i = 0; i < choices.length; i++) {
						if (choices[i].equals(choice)) {
							selection = RevocationReason.reasons[i];
							break;
						}
					}
					final RevocationReason reason = selection;
					if (!UserCertificatePanel.this.ucm.isRevoked(cert.getSerialNumber())) {
						if (UserCertificatePanel.this.ucm.certs.size() == 1) {
							// no other certificates
							UserCertificatePanel.this.ucm.expires = null;
						} else {
							// determine new expires date for user
							Date newExpires = null;
							for (final X509Certificate c : UserCertificatePanel.this.ucm.certs) {
								if (!c.equals(cert)) {
									if (!UserCertificatePanel.this.ucm.isRevoked(c
											.getSerialNumber())) {
										if ((newExpires == null)
												|| c.getNotAfter().after(newExpires)) {
											newExpires = c.getNotAfter();
										}
									}
								}
							}
							UserCertificatePanel.this.ucm.expires = newExpires;
						}

						final AuthorityWorker worker = new AuthorityWorker(
								UserCertificatePanel.this.owner) {

							@Override
							protected Boolean doRequest() throws IOException {
								return revoke(UserCertificatePanel.this.ucm, cert, reason);
							}

							@Override
							protected void onSuccess() {
								JOptionPane.showMessageDialog(UserCertificatePanel.this.owner,
										MessageFormat.format(Translation
												.get("gb.certificateRevoked"), cert
												.getSerialNumber(), cert.getIssuerDN().getName()),
										Translation.get("gb.revokeCertificate"),
										JOptionPane.INFORMATION_MESSAGE);
							}

						};
						worker.execute();
					}
				}
				catch (final Exception x) {
					Utils.showException(UserCertificatePanel.this, x);
				}
			}
		});

		final JPanel certificateControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		certificateControls.add(this.newCertificateButton);
		certificateControls.add(this.revokeCertificateButton);
		certificatesPanel.add(certificateControls, BorderLayout.SOUTH);

		add(fieldsPanel, BorderLayout.NORTH);
		add(certificatesPanel, BorderLayout.CENTER);
		setEditable(false);
	}

	public void setUserCertificateModel(UserCertificateModel ucm) {
		this.ucm = ucm;
		setEditable(false);
		this.oidsPanel.setUserCertificateModel(ucm);

		this.tableModel.setUserCertificateModel(ucm);
		this.tableModel.fireTableDataChanged();
		Utils.packColumns(this.table, Utils.MARGIN);
	}

	public void setEditable(boolean editable) {
		this.oidsPanel.setEditable(editable);

		this.editUserButton.setEnabled(!editable && (this.ucm != null));
		this.saveUserButton.setEnabled(editable && (this.ucm != null));

		this.newCertificateButton.setEnabled(this.ucm != null);
		this.revokeCertificateButton.setEnabled(false);
	}

	public abstract Date getDefaultExpiration();

	public abstract boolean isAllowEmail();

	public abstract boolean saveUser(String username, UserCertificateModel ucm);

	public abstract boolean newCertificate(UserCertificateModel ucm, X509Metadata metadata,
			boolean sendEmail);

	public abstract boolean revoke(UserCertificateModel ucm, X509Certificate cert,
			RevocationReason reason);
}
