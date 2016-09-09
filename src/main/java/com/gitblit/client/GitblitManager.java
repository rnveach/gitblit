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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ConnectException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

import com.gitblit.Constants;
import com.gitblit.GitBlitException.ForbiddenException;
import com.gitblit.models.FeedModel;
import com.gitblit.utils.Base64;
import com.gitblit.utils.StringUtils;

/**
 * Gitblit Manager issues JSON RPC requests to a Gitblit server.
 *
 * @author James Moger
 *
 */
public class GitblitManager extends JFrame implements RegistrationsDialog.RegistrationListener {

	private static final long serialVersionUID = 1L;
	private static final String SERVER = "server";
	private static final String FEED = "feed";
	private final SimpleDateFormat dateFormat;
	private JTabbedPane serverTabs;
	private final File configFile = new File(System.getProperty("user.home"), ".gitblit/config");

	private final Map<String, GitblitRegistration> registrations = new LinkedHashMap<String, GitblitRegistration>();
	private JMenu recentMenu;
	private final int maxRecentCount = 5;

	private GitblitManager() {
		super();
		this.dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
		this.dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	private void initialize() {
		setContentPane(getCenterPanel());
		setIconImage(new ImageIcon(getClass().getResource("/gitblt-favicon.png")).getImage());
		setTitle("Gitblit Manager v" + Constants.getVersion() + " (" + Constants.getBuildDate()
				+ ")");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				saveSizeAndPosition();
			}

			@Override
			public void windowOpened(WindowEvent event) {
				manageRegistrations();
			}
		});

		setSizeAndPosition();
		loadRegistrations();
		rebuildRecentMenu();
	}

	private void setSizeAndPosition() {
		String sz = null;
		String pos = null;
		try {
			final StoredConfig config = getConfig();
			sz = config.getString("ui", null, "size");
			pos = config.getString("ui", null, "position");
		}
		catch (final Throwable t) {
			t.printStackTrace();
		}

		// try to restore saved window size
		if (StringUtils.isEmpty(sz)) {
			setSize(850, 500);
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
			final Dimension sz = GitblitManager.this.getSize();
			config.setString("ui", null, "size",
					MessageFormat.format("{0,number,0}x{1,number,0}", sz.width, sz.height));
			final Point pos = GitblitManager.this.getLocationOnScreen();
			config.setString("ui", null, "position",
					MessageFormat.format("{0,number,0},{1,number,0}", pos.x, pos.y));
			config.save();
		}
		catch (final Throwable t) {
			Utils.showException(GitblitManager.this, t);
		}
	}

	private JMenuBar setupMenu() {
		final JMenuBar menuBar = new JMenuBar();
		final JMenu serversMenu = new JMenu(Translation.get("gb.servers"));
		menuBar.add(serversMenu);
		this.recentMenu = new JMenu(Translation.get("gb.recent"));
		serversMenu.add(this.recentMenu);

		final JMenuItem manage = new JMenuItem(Translation.get("gb.manage") + "...");
		manage.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK,
				false));
		manage.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				manageRegistrations();
			}
		});
		serversMenu.add(manage);

		return menuBar;
	}

	private JPanel getCenterPanel() {
		this.serverTabs = new JTabbedPane(SwingConstants.TOP);
		final JMenuBar menubar = setupMenu();
		final JPanel panel = new JPanel(new BorderLayout());
		panel.add(menubar, BorderLayout.NORTH);
		panel.add(this.serverTabs, BorderLayout.CENTER);
		return panel;
	}

	private void manageRegistrations() {
		final RegistrationsDialog dialog = new RegistrationsDialog(
				new ArrayList<GitblitRegistration>(this.registrations.values()), this);
		dialog.setLocationRelativeTo(GitblitManager.this);
		dialog.setVisible(true);
	}

	@Override
	public void login(GitblitRegistration reg) {
		if (!reg.savePassword && ((reg.password == null) || (reg.password.length == 0))) {
			// prompt for password
			final EditRegistrationDialog dialog = new EditRegistrationDialog(this, reg, true);
			dialog.setLocationRelativeTo(GitblitManager.this);
			dialog.setVisible(true);
			final GitblitRegistration newReg = dialog.getRegistration();
			if (newReg == null) {
				// user canceled
				return;
			}
			// preserve feeds
			newReg.feeds.addAll(reg.feeds);

			// use new reg
			reg = newReg;
		}

		// login
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		final GitblitRegistration registration = reg;
		final GitblitPanel panel = new GitblitPanel(registration, this);
		final SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {

			@Override
			protected Boolean doInBackground() throws IOException {
				panel.login();
				return true;
			}

			@Override
			protected void done() {
				try {
					get();

					GitblitManager.this.serverTabs.addTab(registration.name, panel);
					final int idx = GitblitManager.this.serverTabs.getTabCount() - 1;
					GitblitManager.this.serverTabs.setSelectedIndex(idx);
					GitblitManager.this.serverTabs.setTabComponentAt(idx, new ClosableTabComponent(
							registration.name, null, GitblitManager.this.serverTabs, panel));
					registration.lastLogin = new Date();
					saveRegistration(registration.name, registration);
					GitblitManager.this.registrations.put(registration.name, registration);
					rebuildRecentMenu();
					if (!registration.savePassword) {
						// clear password
						registration.password = null;
					}
				}
				catch (final Throwable t) {
					final Throwable cause = t.getCause();
					if (cause instanceof ConnectException) {
						JOptionPane.showMessageDialog(GitblitManager.this, cause.getMessage(),
								Translation.get("gb.error"), JOptionPane.ERROR_MESSAGE);
					} else if (cause instanceof ForbiddenException) {
						JOptionPane
								.showMessageDialog(
										GitblitManager.this,
										"This Gitblit server does not allow RPC Management or Administration",
										Translation.get("gb.error"), JOptionPane.ERROR_MESSAGE);
					} else {
						Utils.showException(GitblitManager.this, t);
					}
				}
				finally {
					setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
				}
			}
		};
		worker.execute();
	}

	private void rebuildRecentMenu() {
		this.recentMenu.removeAll();
		final ImageIcon icon = new ImageIcon(getClass().getResource("/gitblt-favicon.png"));
		List<GitblitRegistration> list = new ArrayList<GitblitRegistration>(
				this.registrations.values());
		Collections.sort(list, new Comparator<GitblitRegistration>() {
			@Override
			public int compare(GitblitRegistration o1, GitblitRegistration o2) {
				return o2.lastLogin.compareTo(o1.lastLogin);
			}
		});
		if (list.size() > this.maxRecentCount) {
			list = list.subList(0, this.maxRecentCount);
		}
		for (int i = 0; i < list.size(); i++) {
			final GitblitRegistration reg = list.get(i);
			final JMenuItem item = new JMenuItem(reg.name, icon);
			item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1 + i,
					InputEvent.CTRL_DOWN_MASK, false));
			item.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					login(reg);
				}
			});
			this.recentMenu.add(item);
		}
	}

	private void loadRegistrations() {
		try {
			final StoredConfig config = getConfig();
			final Set<String> servers = config.getSubsections(SERVER);
			for (final String server : servers) {
				Date lastLogin = new Date(0);
				final String date = config.getString(SERVER, server, "lastLogin");
				if (!StringUtils.isEmpty(date)) {
					lastLogin = this.dateFormat.parse(date);
				}
				final String url = config.getString(SERVER, server, "url");
				final String account = config.getString(SERVER, server, "account");
				char[] password;
				final String pw = config.getString(SERVER, server, "password");
				if (StringUtils.isEmpty(pw)) {
					password = new char[0];
				} else {
					password = new String(Base64.decode(pw)).toCharArray();
				}
				final GitblitRegistration reg = new GitblitRegistration(server, url, account,
						password) {
					private static final long serialVersionUID = 1L;

					@Override
					protected void cacheFeeds() {
						writeFeedCache(this);
					}
				};
				final String[] feeds = config.getStringList(SERVER, server, FEED);
				if (feeds != null) {
					// deserialize the field definitions
					for (final String definition : feeds) {
						final FeedModel feed = new FeedModel(definition);
						reg.feeds.add(feed);
					}
				}
				reg.lastLogin = lastLogin;
				loadFeedCache(reg);
				this.registrations.put(reg.name, reg);
			}
		}
		catch (final Throwable t) {
			Utils.showException(GitblitManager.this, t);
		}
	}

	@Override
	public boolean saveRegistration(String name, GitblitRegistration reg) {
		try {
			final StoredConfig config = getConfig();
			if (!StringUtils.isEmpty(name) && !name.equals(reg.name)) {
				// delete old registration
				this.registrations.remove(name);
				config.unsetSection(SERVER, name);
			}

			// update registration
			config.setString(SERVER, reg.name, "url", reg.url);
			config.setString(SERVER, reg.name, "account", reg.account);
			if (reg.savePassword) {
				config.setString(SERVER, reg.name, "password",
						Base64.encodeBytes(new String(reg.password).getBytes("UTF-8")));
			} else {
				config.setString(SERVER, reg.name, "password", "");
			}
			if (reg.lastLogin != null) {
				config.setString(SERVER, reg.name, "lastLogin",
						this.dateFormat.format(reg.lastLogin));
			}
			// serialize the feed definitions
			final List<String> definitions = new ArrayList<String>();
			for (final FeedModel feed : reg.feeds) {
				definitions.add(feed.toString());
			}
			if (definitions.size() > 0) {
				config.setStringList(SERVER, reg.name, FEED, definitions);
			}
			config.save();
			return true;
		}
		catch (final Throwable t) {
			Utils.showException(GitblitManager.this, t);
		}
		return false;
	}

	@Override
	public boolean deleteRegistrations(List<GitblitRegistration> list) {
		boolean success = false;
		try {
			final StoredConfig config = getConfig();
			for (final GitblitRegistration reg : list) {
				config.unsetSection(SERVER, reg.name);
				this.registrations.remove(reg.name);
			}
			config.save();
			success = true;
		}
		catch (final Throwable t) {
			Utils.showException(GitblitManager.this, t);
		}
		return success;
	}

	private StoredConfig getConfig() throws IOException, ConfigInvalidException {
		final FileBasedConfig config = new FileBasedConfig(this.configFile, FS.detect());
		config.load();
		return config;
	}

	private void loadFeedCache(GitblitRegistration reg) {
		final File feedCache = new File(this.configFile.getParentFile(), StringUtils.getSHA1(reg
				.toString()) + ".cache");
		if (!feedCache.exists()) {
			// no cache for this registration
			return;
		}
		try {
			final BufferedReader reader = new BufferedReader(new FileReader(feedCache));
			final Map<String, Date> cache = new HashMap<String, Date>();
			final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
			String line = null;
			while ((line = reader.readLine()) != null) {
				final String[] kvp = line.split("=");
				cache.put(kvp[0], df.parse(kvp[1]));
			}
			reader.close();
			for (final FeedModel feed : reg.feeds) {
				final String name = feed.toString();
				if (cache.containsKey(name)) {
					feed.currentRefreshDate = cache.get(name);
				}
			}
		}
		catch (final Exception e) {
			Utils.showException(GitblitManager.this, e);
		}
	}

	private void writeFeedCache(GitblitRegistration reg) {
		try {
			final File feedCache = new File(this.configFile.getParentFile(),
					StringUtils.getSHA1(reg.toString()) + ".cache");
			final FileWriter writer = new FileWriter(feedCache);
			for (final FeedModel feed : reg.feeds) {
				writer.append(MessageFormat.format("{0}={1,date,yyyy-MM-dd'T'HH:mm:ss}\n",
						feed.toString(), feed.currentRefreshDate));
			}
			writer.close();
		}
		catch (final Exception e) {
			Utils.showException(GitblitManager.this, e);
		}
	}

	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				}
				catch (final Exception e) {
				}
				final GitblitManager frame = new GitblitManager();
				frame.initialize();
				frame.setVisible(true);
			}
		});
	}
}
