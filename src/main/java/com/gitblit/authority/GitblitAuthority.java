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
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.Message;
import javax.swing.ImageIcon;
import javax.swing.InputVerifier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableRowSorter;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.LoggerFactory;

import com.gitblit.ConfigUserService;
import com.gitblit.Constants;
import com.gitblit.FileSettings;
import com.gitblit.IStoredSettings;
import com.gitblit.IUserService;
import com.gitblit.Keys;
import com.gitblit.client.HeaderPanel;
import com.gitblit.client.Translation;
import com.gitblit.models.Mailing;
import com.gitblit.models.UserModel;
import com.gitblit.service.MailService;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.FileUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.utils.X509Utils;
import com.gitblit.utils.X509Utils.RevocationReason;
import com.gitblit.utils.X509Utils.X509Log;
import com.gitblit.utils.X509Utils.X509Metadata;

/**
 * Simple GUI tool for administering Gitblit client certificates.
 *
 * @author James Moger
 *
 */
public class GitblitAuthority extends JFrame implements X509Log {

	private static final long serialVersionUID = 1L;

	private final UserCertificateTableModel tableModel;

	private UserCertificatePanel userCertificatePanel;

	private File folder;

	private IStoredSettings gitblitSettings;

	private IUserService userService;

	private String caKeystorePassword;

	private JTable table;

	private int defaultDuration;

	private final TableRowSorter<UserCertificateTableModel> defaultSorter;

	private MailService mail;

	private JButton certificateDefaultsButton;

	private JButton newSSLCertificate;

	public static void main(String... args) {
		// filter out the baseFolder parameter
		String folder = "data";
		for (int i = 0; i < args.length; i++) {
			final String arg = args[i];
			if (arg.equals("--baseFolder")) {
				if ((i + 1) == args.length) {
					System.out.println("Invalid --baseFolder parameter!");
					System.exit(-1);
				} else if (!".".equals(args[i + 1])) {
					folder = args[i + 1];
				}
				break;
			}
		}
		final String baseFolder = folder;
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				}
				catch (final Exception e) {
				}
				final GitblitAuthority authority = new GitblitAuthority();
				authority.initialize(baseFolder);
				authority.setLocationRelativeTo(null);
				authority.setVisible(true);
			}
		});
	}

	public GitblitAuthority() {
		super();
		this.tableModel = new UserCertificateTableModel();
		this.defaultSorter = new TableRowSorter<UserCertificateTableModel>(this.tableModel);
	}

	public void initialize(String baseFolder) {
		setIconImage(new ImageIcon(getClass().getResource("/gitblt-favicon.png")).getImage());
		setTitle("Gitblit Certificate Authority v" + Constants.getVersion() + " ("
				+ Constants.getBuildDate() + ")");
		setContentPane(getUI());
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				saveSizeAndPosition();
			}

			@Override
			public void windowOpened(WindowEvent event) {
			}
		});

		final File folder = new File(baseFolder).getAbsoluteFile();
		load(folder);

		setSizeAndPosition();
	}

	private void setSizeAndPosition() {
		String sz = null;
		String pos = null;
		try {
			final StoredConfig config = getConfig();
			sz = config.getString("ui", null, "size");
			pos = config.getString("ui", null, "position");
			this.defaultDuration = config.getInt("new", "duration", 365);
		}
		catch (final Throwable t) {
			t.printStackTrace();
		}

		// try to restore saved window size
		if (StringUtils.isEmpty(sz)) {
			setSize(900, 600);
		} else {
			final String[] chunks = sz.split("x");
			final int width = Integer.parseInt(chunks[0]);
			final int height = Integer.parseInt(chunks[1]);
			setSize(width, height);
		}

		// try to restore saved window position
		if (StringUtils.isEmpty(pos)) {
			setLocationRelativeTo(null);
		} else {
			final String[] chunks = pos.split(",");
			final int x = Integer.parseInt(chunks[0]);
			final int y = Integer.parseInt(chunks[1]);
			setLocation(x, y);
		}
	}

	private void saveSizeAndPosition() {
		try {
			// save window size and position
			final StoredConfig config = getConfig();
			final Dimension sz = GitblitAuthority.this.getSize();
			config.setString("ui", null, "size",
					MessageFormat.format("{0,number,0}x{1,number,0}", sz.width, sz.height));
			final Point pos = GitblitAuthority.this.getLocationOnScreen();
			config.setString("ui", null, "position",
					MessageFormat.format("{0,number,0},{1,number,0}", pos.x, pos.y));
			config.save();
		}
		catch (final Throwable t) {
			Utils.showException(GitblitAuthority.this, t);
		}
	}

	private StoredConfig getConfig() throws IOException, ConfigInvalidException {
		final File configFile = new File(this.folder, X509Utils.CA_CONFIG);
		final FileBasedConfig config = new FileBasedConfig(configFile, FS.detect());
		config.load();
		return config;
	}

	private IUserService loadUsers(File folder) {
		final File file = new File(folder, "gitblit.properties");
		if (!file.exists()) {
			return null;
		}
		this.gitblitSettings = new FileSettings(file.getAbsolutePath());
		this.mail = new MailService(this.gitblitSettings);
		String us = this.gitblitSettings.getString(Keys.realm.userService,
				"${baseFolder}/users.conf");
		final String ext = us.substring(us.lastIndexOf(".") + 1).toLowerCase();
		IUserService service = null;
		if (!ext.equals("conf") && !ext.equals("properties") && ext.contains("userservice")) {
			final String realm = ext.substring(0, ext.indexOf("userservice"));
			us = this.gitblitSettings.getString(
					MessageFormat.format("realm.{0}.backingUserService", realm),
					"${baseFolder}/users.conf");
		}

		if (us.endsWith(".conf")) {
			service = new ConfigUserService(FileUtils.resolveParameter(Constants.baseFolder$,
					folder, us));
		} else {
			throw new RuntimeException("Unsupported user service: " + us);
		}

		service = new ConfigUserService(FileUtils.resolveParameter(Constants.baseFolder$, folder,
				us));
		return service;
	}

	private void load(File folder) {
		this.folder = folder;
		this.userService = loadUsers(folder);
		System.out.println(Constants.baseFolder$ + " set to " + folder);
		if (this.userService == null) {
			JOptionPane.showMessageDialog(this, MessageFormat.format(
					"Sorry, {0} doesn't look like a Gitblit GO installation.", folder));
		} else {
			// build empty certificate model for all users
			final Map<String, UserCertificateModel> map = new HashMap<String, UserCertificateModel>();
			for (final String user : this.userService.getAllUsernames()) {
				final UserModel model = this.userService.getUserModel(user);
				final UserCertificateModel ucm = new UserCertificateModel(model);
				map.put(user, ucm);
			}
			final File certificatesConfigFile = new File(folder, X509Utils.CA_CONFIG);
			final FileBasedConfig config = new FileBasedConfig(certificatesConfigFile, FS.detect());
			if (certificatesConfigFile.exists()) {
				try {
					config.load();
					// replace user certificate model with actual data
					final List<UserCertificateModel> list = UserCertificateConfig.KEY.parse(config).list;
					for (final UserCertificateModel ucm : list) {
						ucm.user = this.userService.getUserModel(ucm.user.username);
						map.put(ucm.user.username, ucm);
					}
				}
				catch (final IOException e) {
					e.printStackTrace();
				}
				catch (final ConfigInvalidException e) {
					e.printStackTrace();
				}
			}

			this.tableModel.list = new ArrayList<UserCertificateModel>(map.values());
			Collections.sort(this.tableModel.list);
			this.tableModel.fireTableDataChanged();
			Utils.packColumns(this.table, Utils.MARGIN);

			final File caKeystore = new File(folder, X509Utils.CA_KEY_STORE);
			if (!caKeystore.exists()) {

				if (!X509Utils.unlimitedStrength) {
					// prompt to confirm user understands JCE Standard Strength
					// encryption
					final int res = JOptionPane.showConfirmDialog(GitblitAuthority.this,
							Translation.get("gb.jceWarning"), Translation.get("gb.warning"),
							JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
					if (res != JOptionPane.YES_OPTION) {
						if (Desktop.isDesktopSupported()) {
							if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
								try {
									Desktop.getDesktop()
											.browse(URI
													.create("http://www.oracle.com/technetwork/java/javase/downloads/index.html"));
								}
								catch (final IOException e) {
								}
							}
						}
						System.exit(1);
					}
				}

				// show certificate defaults dialog
				this.certificateDefaultsButton.doClick();

				// create "localhost" ssl certificate
				prepareX509Infrastructure();
			}
		}
	}

	private boolean prepareX509Infrastructure() {
		if (this.caKeystorePassword == null) {
			final JPasswordField pass = new JPasswordField(10);
			pass.setText(this.caKeystorePassword);
			pass.addAncestorListener(new RequestFocusListener());
			final JPanel panel = new JPanel(new BorderLayout());
			panel.add(new JLabel(Translation.get("gb.enterKeystorePassword")), BorderLayout.NORTH);
			panel.add(pass, BorderLayout.CENTER);
			final int result = JOptionPane.showConfirmDialog(GitblitAuthority.this, panel,
					Translation.get("gb.password"), JOptionPane.OK_CANCEL_OPTION);
			if (result == JOptionPane.OK_OPTION) {
				this.caKeystorePassword = new String(pass.getPassword());
			} else {
				return false;
			}
		}

		final X509Metadata metadata = new X509Metadata("localhost", this.caKeystorePassword);
		setMetadataDefaults(metadata);
		metadata.notAfter = new Date(System.currentTimeMillis() + (10 * TimeUtils.ONEYEAR));
		X509Utils.prepareX509Infrastructure(metadata, this.folder, this);
		return true;
	}

	private List<X509Certificate> findCerts(File folder, String username) {
		final List<X509Certificate> list = new ArrayList<X509Certificate>();
		final File userFolder = new File(folder, X509Utils.CERTS + File.separator + username);
		if (!userFolder.exists()) {
			return list;
		}
		final File[] certs = userFolder.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".cer") || name.toLowerCase().endsWith(".crt");
			}
		});
		try {
			final CertificateFactory factory = CertificateFactory.getInstance("X.509");
			for (final File cert : certs) {
				final BufferedInputStream is = new BufferedInputStream(new FileInputStream(cert));
				final X509Certificate x509 = (X509Certificate) factory.generateCertificate(is);
				is.close();
				list.add(x509);
			}
		}
		catch (final Exception e) {
			Utils.showException(GitblitAuthority.this, e);
		}
		return list;
	}

	private Container getUI() {
		this.userCertificatePanel = new UserCertificatePanel(this) {

			private static final long serialVersionUID = 1L;

			@Override
			public Insets getInsets() {
				return Utils.INSETS;
			}

			@Override
			public boolean isAllowEmail() {
				return GitblitAuthority.this.mail.isReady();
			}

			@Override
			public Date getDefaultExpiration() {
				final Calendar c = Calendar.getInstance();
				c.add(Calendar.DATE, GitblitAuthority.this.defaultDuration);
				c.set(Calendar.HOUR_OF_DAY, 0);
				c.set(Calendar.MINUTE, 0);
				c.set(Calendar.SECOND, 0);
				c.set(Calendar.MILLISECOND, 0);
				return c.getTime();
			}

			@Override
			public boolean saveUser(String username, UserCertificateModel ucm) {
				return GitblitAuthority.this.userService.updateUserModel(username, ucm.user);
			}

			@Override
			public boolean newCertificate(UserCertificateModel ucm, X509Metadata metadata,
					boolean sendEmail) {
				if (!prepareX509Infrastructure()) {
					return false;
				}

				final Date notAfter = metadata.notAfter;
				setMetadataDefaults(metadata);
				metadata.notAfter = notAfter;

				// set user's specified OID values
				final UserModel user = ucm.user;
				if (!StringUtils.isEmpty(user.organizationalUnit)) {
					metadata.oids.put("OU", user.organizationalUnit);
				}
				if (!StringUtils.isEmpty(user.organization)) {
					metadata.oids.put("O", user.organization);
				}
				if (!StringUtils.isEmpty(user.locality)) {
					metadata.oids.put("L", user.locality);
				}
				if (!StringUtils.isEmpty(user.stateProvince)) {
					metadata.oids.put("ST", user.stateProvince);
				}
				if (!StringUtils.isEmpty(user.countryCode)) {
					metadata.oids.put("C", user.countryCode);
				}

				final File caKeystoreFile = new File(GitblitAuthority.this.folder,
						X509Utils.CA_KEY_STORE);
				final File zip = X509Utils.newClientBundle(metadata, caKeystoreFile,
						GitblitAuthority.this.caKeystorePassword, GitblitAuthority.this);

				// save latest expiration date
				if ((ucm.expires == null) || metadata.notAfter.before(ucm.expires)) {
					ucm.expires = metadata.notAfter;
				}

				updateAuthorityConfig(ucm);

				// refresh user
				ucm.certs = null;
				final int selectedIndex = GitblitAuthority.this.table.getSelectedRow();
				GitblitAuthority.this.tableModel.fireTableDataChanged();
				GitblitAuthority.this.table.getSelectionModel().setSelectionInterval(selectedIndex,
						selectedIndex);

				if (sendEmail) {
					sendEmail(user, metadata, zip);
				}
				return true;
			}

			@Override
			public boolean revoke(UserCertificateModel ucm, X509Certificate cert,
					RevocationReason reason) {
				if (!prepareX509Infrastructure()) {
					return false;
				}

				final File caRevocationList = new File(GitblitAuthority.this.folder,
						X509Utils.CA_REVOCATION_LIST);
				final File caKeystoreFile = new File(GitblitAuthority.this.folder,
						X509Utils.CA_KEY_STORE);
				if (X509Utils.revoke(cert, reason, caRevocationList, caKeystoreFile,
						GitblitAuthority.this.caKeystorePassword, GitblitAuthority.this)) {
					final File certificatesConfigFile = new File(GitblitAuthority.this.folder,
							X509Utils.CA_CONFIG);
					final FileBasedConfig config = new FileBasedConfig(certificatesConfigFile,
							FS.detect());
					if (certificatesConfigFile.exists()) {
						try {
							config.load();
						}
						catch (final Exception e) {
							Utils.showException(GitblitAuthority.this, e);
						}
					}
					// add serial to revoked list
					ucm.revoke(cert.getSerialNumber(), reason);
					ucm.update(config);
					try {
						config.save();
					}
					catch (final Exception e) {
						Utils.showException(GitblitAuthority.this, e);
					}

					// refresh user
					ucm.certs = null;
					final int modelIndex = GitblitAuthority.this.table
							.convertRowIndexToModel(GitblitAuthority.this.table.getSelectedRow());
					GitblitAuthority.this.tableModel.fireTableDataChanged();
					GitblitAuthority.this.table.getSelectionModel().setSelectionInterval(
							modelIndex, modelIndex);

					return true;
				}

				return false;
			}
		};

		this.table = Utils.newTable(this.tableModel, Utils.DATE_FORMAT);
		this.table.setRowSorter(this.defaultSorter);
		this.table.setDefaultRenderer(CertificateStatus.class, new CertificateStatusRenderer());
		this.table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) {
					return;
				}
				final int row = GitblitAuthority.this.table.getSelectedRow();
				if (row < 0) {
					return;
				}
				final int modelIndex = GitblitAuthority.this.table.convertRowIndexToModel(row);
				final UserCertificateModel ucm = GitblitAuthority.this.tableModel.get(modelIndex);
				if (ucm.certs == null) {
					ucm.certs = findCerts(GitblitAuthority.this.folder, ucm.user.username);
				}
				GitblitAuthority.this.userCertificatePanel.setUserCertificateModel(ucm);
			}
		});

		final JPanel usersPanel = new JPanel(new BorderLayout()) {

			private static final long serialVersionUID = 1L;

			@Override
			public Insets getInsets() {
				return Utils.INSETS;
			}
		};
		usersPanel.add(new HeaderPanel(Translation.get("gb.users"), "users_16x16.png"),
				BorderLayout.NORTH);
		usersPanel.add(new JScrollPane(this.table), BorderLayout.CENTER);
		usersPanel.setMinimumSize(new Dimension(400, 10));

		this.certificateDefaultsButton = new JButton(new ImageIcon(getClass().getResource(
				"/settings_16x16.png")));
		this.certificateDefaultsButton.setFocusable(false);
		this.certificateDefaultsButton.setToolTipText(Translation.get("gb.newCertificateDefaults"));
		this.certificateDefaultsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				final X509Metadata metadata = new X509Metadata("whocares", "whocares");
				final File certificatesConfigFile = new File(GitblitAuthority.this.folder,
						X509Utils.CA_CONFIG);
				final FileBasedConfig config = new FileBasedConfig(certificatesConfigFile, FS
						.detect());
				NewCertificateConfig certificateConfig = null;
				if (certificatesConfigFile.exists()) {
					try {
						config.load();
					}
					catch (final Exception x) {
						Utils.showException(GitblitAuthority.this, x);
					}
					certificateConfig = NewCertificateConfig.KEY.parse(config);
					certificateConfig.update(metadata);
				}
				final InputVerifier verifier = new InputVerifier() {
					@Override
					public boolean verify(JComponent comp) {
						boolean returnValue;
						final JTextField textField = (JTextField) comp;
						try {
							Integer.parseInt(textField.getText());
							returnValue = true;
						}
						catch (final NumberFormatException e) {
							returnValue = false;
						}
						return returnValue;
					}
				};

				final JTextField siteNameTF = new JTextField(20);
				siteNameTF.setText(GitblitAuthority.this.gitblitSettings.getString(
						Keys.web.siteName, "Gitblit"));
				final JPanel siteNamePanel = Utils.newFieldPanel(Translation.get("gb.siteName"),
						siteNameTF, Translation.get("gb.siteNameDescription"));

				final JTextField validityTF = new JTextField(4);
				validityTF.setInputVerifier(verifier);
				validityTF.setVerifyInputWhenFocusTarget(true);
				validityTF.setText("" + certificateConfig.duration);
				final JPanel validityPanel = Utils.newFieldPanel(Translation.get("gb.validity"),
						validityTF, Translation.get("gb.duration.days").replace("{0}", "").trim());

				final JPanel p1 = new JPanel(new GridLayout(0, 1, 5, 2));
				p1.add(siteNamePanel);
				p1.add(validityPanel);

				final DefaultOidsPanel oids = new DefaultOidsPanel(metadata);

				final JPanel panel = new JPanel(new BorderLayout());
				panel.add(p1, BorderLayout.NORTH);
				panel.add(oids, BorderLayout.CENTER);

				final int result = JOptionPane.showConfirmDialog(GitblitAuthority.this, panel,
						Translation.get("gb.newCertificateDefaults"), JOptionPane.OK_CANCEL_OPTION,
						JOptionPane.QUESTION_MESSAGE,
						new ImageIcon(getClass().getResource("/settings_32x32.png")));
				if (result == JOptionPane.OK_OPTION) {
					try {
						oids.update(metadata);
						certificateConfig.duration = Integer.parseInt(validityTF.getText());
						certificateConfig.store(config, metadata);
						config.save();

						final Map<String, String> updates = new HashMap<String, String>();
						updates.put(Keys.web.siteName, siteNameTF.getText());
						GitblitAuthority.this.gitblitSettings.saveSettings(updates);
					}
					catch (final Exception e1) {
						Utils.showException(GitblitAuthority.this, e1);
					}
				}
			}
		});

		this.newSSLCertificate = new JButton(new ImageIcon(getClass().getResource(
				"/rosette_16x16.png")));
		this.newSSLCertificate.setFocusable(false);
		this.newSSLCertificate.setToolTipText(Translation.get("gb.newSSLCertificate"));
		this.newSSLCertificate.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				final Date defaultExpiration = new Date(System.currentTimeMillis()
						+ (10 * TimeUtils.ONEYEAR));
				final NewSSLCertificateDialog dialog = new NewSSLCertificateDialog(
						GitblitAuthority.this, defaultExpiration);
				dialog.setModal(true);
				dialog.setVisible(true);
				if (dialog.isCanceled()) {
					return;
				}
				final Date expires = dialog.getExpiration();
				final String hostname = dialog.getHostname();
				final boolean serveCertificate = dialog.isServeCertificate();

				final AuthorityWorker worker = new AuthorityWorker(GitblitAuthority.this) {

					@Override
					protected Boolean doRequest() throws IOException {
						if (!prepareX509Infrastructure()) {
							return false;
						}

						// read CA private key and certificate
						final File caKeystoreFile = new File(GitblitAuthority.this.folder,
								X509Utils.CA_KEY_STORE);
						final PrivateKey caPrivateKey = X509Utils.getPrivateKey(X509Utils.CA_ALIAS,
								caKeystoreFile, GitblitAuthority.this.caKeystorePassword);
						final X509Certificate caCert = X509Utils.getCertificate(X509Utils.CA_ALIAS,
								caKeystoreFile, GitblitAuthority.this.caKeystorePassword);

						// generate new SSL certificate
						final X509Metadata metadata = new X509Metadata(hostname,
								GitblitAuthority.this.caKeystorePassword);
						setMetadataDefaults(metadata);
						metadata.notAfter = expires;
						final File serverKeystoreFile = new File(GitblitAuthority.this.folder,
								X509Utils.SERVER_KEY_STORE);
						final X509Certificate cert = X509Utils.newSSLCertificate(metadata,
								caPrivateKey, caCert, serverKeystoreFile, GitblitAuthority.this);
						final boolean hasCert = cert != null;
						if (hasCert && serveCertificate) {
							// update Gitblit https connector alias
							final Map<String, String> updates = new HashMap<String, String>();
							updates.put(Keys.server.certificateAlias, metadata.commonName);
							GitblitAuthority.this.gitblitSettings.saveSettings(updates);
						}
						return hasCert;
					}

					@Override
					protected void onSuccess() {
						if (serveCertificate) {
							JOptionPane.showMessageDialog(GitblitAuthority.this, MessageFormat
									.format(Translation.get("gb.sslCertificateGeneratedRestart"),
											hostname), Translation.get("gb.newSSLCertificate"),
									JOptionPane.INFORMATION_MESSAGE);
						} else {
							JOptionPane.showMessageDialog(
									GitblitAuthority.this,
									MessageFormat.format(
											Translation.get("gb.sslCertificateGenerated"), hostname),
									Translation.get("gb.newSSLCertificate"),
									JOptionPane.INFORMATION_MESSAGE);
						}
					}
				};

				worker.execute();
			}
		});

		final JButton emailBundle = new JButton(new ImageIcon(getClass().getResource(
				"/mail_16x16.png")));
		emailBundle.setFocusable(false);
		emailBundle.setToolTipText(Translation.get("gb.emailCertificateBundle"));
		emailBundle.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				final int row = GitblitAuthority.this.table.getSelectedRow();
				if (row < 0) {
					return;
				}
				final int modelIndex = GitblitAuthority.this.table.convertRowIndexToModel(row);
				final UserCertificateModel ucm = GitblitAuthority.this.tableModel.get(modelIndex);
				if (ArrayUtils.isEmpty(ucm.certs)) {
					JOptionPane.showMessageDialog(
							GitblitAuthority.this,
							MessageFormat.format(
									Translation.get("gb.pleaseGenerateClientCertificate"),
									ucm.user.getDisplayName()));
				}
				final File zip = new File(GitblitAuthority.this.folder, X509Utils.CERTS
						+ File.separator + ucm.user.username + File.separator + ucm.user.username
						+ ".zip");
				if (!zip.exists()) {
					return;
				}

				final AuthorityWorker worker = new AuthorityWorker(GitblitAuthority.this) {
					@Override
					protected Boolean doRequest() throws IOException {
						final X509Metadata metadata = new X509Metadata(ucm.user.username,
								"whocares");
						metadata.serverHostname = GitblitAuthority.this.gitblitSettings.getString(
								Keys.web.siteName, Constants.NAME);
						if (StringUtils.isEmpty(metadata.serverHostname)) {
							metadata.serverHostname = Constants.NAME;
						}
						metadata.userDisplayname = ucm.user.getDisplayName();
						return sendEmail(ucm.user, metadata, zip);
					}

					@Override
					protected void onSuccess() {
						JOptionPane.showMessageDialog(
								GitblitAuthority.this,
								MessageFormat.format(
										Translation.get("gb.clientCertificateBundleSent"),
										ucm.user.getDisplayName()));
					}

				};
				worker.execute();
			}
		});

		final JButton logButton = new JButton(new ImageIcon(getClass().getResource(
				"/script_16x16.png")));
		logButton.setFocusable(false);
		logButton.setToolTipText(Translation.get("gb.log"));
		logButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				final File log = new File(GitblitAuthority.this.folder, X509Utils.CERTS
						+ File.separator + "log.txt");
				if (log.exists()) {
					final String content = FileUtils.readContent(log, "\n");
					final JTextArea textarea = new JTextArea(content);
					final JScrollPane scrollPane = new JScrollPane(textarea);
					scrollPane.setPreferredSize(new Dimension(700, 400));
					JOptionPane.showMessageDialog(GitblitAuthority.this, scrollPane,
							log.getAbsolutePath(), JOptionPane.INFORMATION_MESSAGE);
				}
			}
		});

		final JTextField filterTextfield = new JTextField(15);
		filterTextfield.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				filterUsers(filterTextfield.getText());
			}
		});
		filterTextfield.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				filterUsers(filterTextfield.getText());
			}
		});

		final JToolBar buttonControls = new JToolBar(SwingConstants.HORIZONTAL);
		buttonControls.setFloatable(false);
		buttonControls.add(this.certificateDefaultsButton);
		buttonControls.add(this.newSSLCertificate);
		buttonControls.add(emailBundle);
		buttonControls.add(logButton);

		final JPanel userControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, Utils.MARGIN,
				Utils.MARGIN));
		userControls.add(new JLabel(Translation.get("gb.filter")));
		userControls.add(filterTextfield);

		final JPanel topPanel = new JPanel(new BorderLayout(0, 0));
		topPanel.add(buttonControls, BorderLayout.WEST);
		topPanel.add(userControls, BorderLayout.EAST);

		final JPanel leftPanel = new JPanel(new BorderLayout());
		leftPanel.add(topPanel, BorderLayout.NORTH);
		leftPanel.add(usersPanel, BorderLayout.CENTER);

		this.userCertificatePanel.setMinimumSize(new Dimension(375, 10));

		final JLabel statusLabel = new JLabel();
		statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		if (X509Utils.unlimitedStrength) {
			statusLabel.setText("JCE Unlimited Strength Jurisdiction Policy");
		} else {
			statusLabel.setText("JCE Standard Encryption Policy");
		}

		final JPanel root = new JPanel(new BorderLayout()) {
			private static final long serialVersionUID = 1L;

			@Override
			public Insets getInsets() {
				return Utils.INSETS;
			}
		};
		final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel,
				this.userCertificatePanel);
		splitPane.setDividerLocation(1d);
		root.add(splitPane, BorderLayout.CENTER);
		root.add(statusLabel, BorderLayout.SOUTH);
		return root;
	}

	private void filterUsers(final String fragment) {
		this.table.clearSelection();
		this.userCertificatePanel.setUserCertificateModel(null);
		if (StringUtils.isEmpty(fragment)) {
			this.table.setRowSorter(this.defaultSorter);
			return;
		}
		final RowFilter<UserCertificateTableModel, Object> containsFilter = new RowFilter<UserCertificateTableModel, Object>() {
			@Override
			public boolean include(
					Entry<? extends UserCertificateTableModel, ? extends Object> entry) {
				for (int i = entry.getValueCount() - 1; i >= 0; i--) {
					if (entry.getStringValue(i).toLowerCase().contains(fragment.toLowerCase())) {
						return true;
					}
				}
				return false;
			}
		};
		final TableRowSorter<UserCertificateTableModel> sorter = new TableRowSorter<UserCertificateTableModel>(
				this.tableModel);
		sorter.setRowFilter(containsFilter);
		this.table.setRowSorter(sorter);
	}

	@Override
	public void log(String message) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(new File(this.folder, X509Utils.CERTS
					+ File.separator + "log.txt"), true));
			writer.write(MessageFormat
					.format("{0,date,yyyy-MM-dd HH:mm}: {1}", new Date(), message));
			writer.newLine();
			writer.flush();
		}
		catch (final Exception e) {
			LoggerFactory.getLogger(GitblitAuthority.class).error("Failed to append log entry!", e);
		}
		finally {
			if (writer != null) {
				try {
					writer.close();
				}
				catch (final IOException e) {
				}
			}
		}
	}

	private boolean sendEmail(UserModel user, X509Metadata metadata, File zip) {
		// send email
		try {
			if (this.mail.isReady()) {
				final Mailing mailing = Mailing.newPlain();
				mailing.subject = "Your Gitblit client certificate for " + metadata.serverHostname;
				mailing.setRecipients(user.emailAddress);
				String body = X509Utils.processTemplate(new File(this.folder, X509Utils.CERTS
						+ File.separator + "mail.tmpl"), metadata);
				if (StringUtils.isEmpty(body)) {
					body = MessageFormat
							.format("Hi {0}\n\nHere is your client certificate bundle.\nInside the zip file are installation instructions.",
									user.getDisplayName());
				}
				mailing.content = body;
				mailing.addAttachment(zip);

				final Message message = this.mail.createMessage(mailing);

				this.mail.sendNow(message);
				return true;
			} else {
				JOptionPane
						.showMessageDialog(
								GitblitAuthority.this,
								"Sorry, the mail server settings are not configured properly.\nCan not send email.",
								Translation.get("gb.error"), JOptionPane.ERROR_MESSAGE);
			}
		}
		catch (final Exception e) {
			Utils.showException(GitblitAuthority.this, e);
		}
		return false;
	}

	private void setMetadataDefaults(X509Metadata metadata) {
		metadata.serverHostname = this.gitblitSettings.getString(Keys.web.siteName, Constants.NAME);
		if (StringUtils.isEmpty(metadata.serverHostname)) {
			metadata.serverHostname = Constants.NAME;
		}

		// set default values from config file
		final File certificatesConfigFile = new File(this.folder, X509Utils.CA_CONFIG);
		final FileBasedConfig config = new FileBasedConfig(certificatesConfigFile, FS.detect());
		if (certificatesConfigFile.exists()) {
			try {
				config.load();
			}
			catch (final Exception e) {
				Utils.showException(GitblitAuthority.this, e);
			}
			final NewCertificateConfig certificateConfig = NewCertificateConfig.KEY.parse(config);
			certificateConfig.update(metadata);
		}
	}

	private void updateAuthorityConfig(UserCertificateModel ucm) {
		final File certificatesConfigFile = new File(this.folder, X509Utils.CA_CONFIG);
		final FileBasedConfig config = new FileBasedConfig(certificatesConfigFile, FS.detect());
		if (certificatesConfigFile.exists()) {
			try {
				config.load();
			}
			catch (final Exception e) {
				Utils.showException(GitblitAuthority.this, e);
			}
		}
		ucm.update(config);
		try {
			config.save();
		}
		catch (final Exception e) {
			Utils.showException(GitblitAuthority.this, e);
		}
	}
}
