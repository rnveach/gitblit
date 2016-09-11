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
package com.gitblit;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.ProtectionDomain;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import org.apache.log4j.PropertyConfigurator;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.authority.GitblitAuthority;
import com.gitblit.authority.NewCertificateConfig;
import com.gitblit.servlet.GitblitContext;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.utils.X509Utils;
import com.gitblit.utils.X509Utils.X509Log;
import com.gitblit.utils.X509Utils.X509Metadata;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldif.LDIFReader;

/**
 * GitBlitServer is the embedded Jetty server for Gitblit GO. This class starts
 * and stops an instance of Jetty that is configured from a combination of the
 * gitblit.properties file and command line parameters. JCommander is used to
 * simplify command line parameter processing. This class also automatically
 * generates a self-signed certificate for localhost, if the keystore does not
 * already exist.
 *
 * @author James Moger
 *
 */
public class GitBlitServer {

	private static Logger logger;

	public static void main(String... args) {
		final GitBlitServer server = new GitBlitServer();

		// filter out the baseFolder parameter
		final List<String> filtered = new ArrayList<String>();
		String folder = "data";
		for (int i = 0; i < args.length; i++) {
			final String arg = args[i];
			if (arg.equals("--baseFolder")) {
				if ((i + 1) == args.length) {
					System.out.println("Invalid --baseFolder parameter!");
					throw new RuntimeException("System.exit(-1);");
				} else if (!".".equals(args[i + 1])) {
					folder = args[i + 1];
				}
				i = i + 1;
			} else {
				filtered.add(arg);
			}
		}

		Params.baseFolder = folder;
		final Params params = new Params();
		final CmdLineParser parser = new CmdLineParser(params);
		try {
			parser.parseArgument(filtered);
			if (params.help) {
				GitBlitServer.usage(parser, null);
			}
		}
		catch (final CmdLineException t) {
			GitBlitServer.usage(parser, t);
		}

		if (params.stop) {
			server.stop(params);
		} else {
			server.start(params);
		}
	}

	/**
	 * Display the command line usage of Gitblit GO.
	 *
	 * @param parser
	 * @param t
	 */
	protected static final void usage(CmdLineParser parser, CmdLineException t) {
		System.out.println(Constants.BORDER);
		System.out.println(Constants.getGitBlitVersion());
		System.out.println(Constants.BORDER);
		System.out.println();
		if (t != null) {
			System.out.println(t.getMessage());
			System.out.println();
		}
		if (parser != null) {
			parser.printUsage(System.out);
			System.out.println(
					"\nExample:\n  java -server -Xmx1024M -jar gitblit.jar --repositoriesFolder c:\\git --httpPort 80 --httpsPort 443");
		}
		throw new RuntimeException("System.exit(0);");
	}

	private static File getBaseFolder(Params params) {
		String path = System.getProperty("GITBLIT_HOME", Params.baseFolder);
		if (!StringUtils.isEmpty(System.getenv("GITBLIT_HOME"))) {
			path = System.getenv("GITBLIT_HOME");
		}

		return new File(path).getAbsoluteFile();
	}

	/**
	 * Stop Gitblt GO.
	 */
	@SuppressWarnings("static-method")
	public void stop(Params params) {
		try {
			final Socket s = new Socket(InetAddress.getByName("127.0.0.1"), params.shutdownPort);
			final OutputStream out = s.getOutputStream();
			System.out.println("Sending Shutdown Request to " + Constants.NAME);
			out.write("\r\n".getBytes());
			out.flush();
			s.close();
		}
		catch (final UnknownHostException e) {
			e.printStackTrace();
		}
		catch (final IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Start Gitblit GO.
	 */
	protected final void start(Params params) {
		final File baseFolder = getBaseFolder(params);
		FileSettings settings = params.FILESETTINGS;
		if (!StringUtils.isEmpty(params.settingsfile)) {
			if (new File(params.settingsfile).exists()) {
				settings = new FileSettings(params.settingsfile);
			}
		}

		if (params.dailyLogFile) {
			// Configure log4j for daily log file generation
			InputStream is = null;
			try {
				is = getClass().getResourceAsStream("/log4j.properties");
				final Properties loggingProperties = new Properties();
				loggingProperties.load(is);

				loggingProperties.put("log4j.appender.R.File",
						new File(baseFolder, "logs/gitblit.log").getAbsolutePath());
				loggingProperties.put("log4j.rootCategory", "INFO, R");

				if (settings.getBoolean(Keys.web.debugMode, false)) {
					loggingProperties.put("log4j.logger.com.gitblit", "DEBUG");
				}

				PropertyConfigurator.configure(loggingProperties);
			}
			catch (final Exception e) {
				e.printStackTrace();
			}
			finally {
				try {
					if (is != null) {
						is.close();
					}
				}
				catch (final IOException e) {
					e.printStackTrace();
				}
			}
		}

		logger = LoggerFactory.getLogger(GitBlitServer.class);
		logger.info("\n" + Constants.getASCIIArt());

		System.setProperty("java.awt.headless", "true");

		final String osname = System.getProperty("os.name");
		final String osversion = System.getProperty("os.version");
		logger.info("Running on " + osname + " (" + osversion + ")");

		final QueuedThreadPool threadPool = new QueuedThreadPool();
		final int maxThreads = settings.getInteger(Keys.server.threadPoolSize, 50);
		if (maxThreads > 0) {
			threadPool.setMaxThreads(maxThreads);
		}

		final Server server = new Server(threadPool);
		server.setStopAtShutdown(true);

		// conditionally configure the https connector
		if (params.securePort > 0) {
			final File certificatesConf = new File(baseFolder, X509Utils.CA_CONFIG);
			final File serverKeyStore = new File(baseFolder, X509Utils.SERVER_KEY_STORE);
			final File serverTrustStore = new File(baseFolder, X509Utils.SERVER_TRUST_STORE);
			final File caRevocationList = new File(baseFolder, X509Utils.CA_REVOCATION_LIST);

			// generate CA & web certificates, create certificate stores
			final X509Metadata metadata = new X509Metadata("localhost", params.storePassword);
			// set default certificate values from config file
			if (certificatesConf.exists()) {
				final FileBasedConfig config = new FileBasedConfig(certificatesConf, FS.detect());
				try {
					config.load();
				}
				catch (final Exception e) {
					logger.error("Error parsing " + certificatesConf, e);
				}
				final NewCertificateConfig certificateConfig = NewCertificateConfig.KEY
						.parse(config);
				certificateConfig.update(metadata);
			}

			metadata.notAfter = new Date(System.currentTimeMillis() + (10 * TimeUtils.ONEYEAR));
			X509Utils.prepareX509Infrastructure(metadata, baseFolder, new X509Log() {
				@Override
				public void log(String message) {
					BufferedWriter writer = null;
					try {
						writer = new BufferedWriter(new FileWriter(
								new File(baseFolder, X509Utils.CERTS + File.separator + "log.txt"),
								true));
						writer.write(MessageFormat.format("{0,date,yyyy-MM-dd HH:mm}: {1}",
								new Date(), message));
						writer.newLine();
						writer.flush();
					}
					catch (final Exception e) {
						LoggerFactory.getLogger(GitblitAuthority.class)
								.error("Failed to append log entry!", e);
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
			});

			if (serverKeyStore.exists()) {
				/*
				 * HTTPS
				 */
				logger.info("Setting up HTTPS transport on port " + params.securePort);
				final GitblitSslContextFactory factory = new GitblitSslContextFactory(params.alias,
						serverKeyStore, serverTrustStore, params.storePassword, caRevocationList);
				if (params.requireClientCertificates) {
					factory.setNeedClientAuth(true);
				} else {
					factory.setWantClientAuth(true);
				}

				final ServerConnector connector = new ServerConnector(server, factory);
				connector.setSoLingerTime(-1);
				connector.setIdleTimeout(30000);
				connector.setPort(params.securePort);
				final String bindInterface = settings.getString(Keys.server.httpsBindInterface,
						null);
				if (!StringUtils.isEmpty(bindInterface)) {
					logger.warn(MessageFormat.format(
							"Binding HTTPS transport on port {0,number,0} to {1}",
							params.securePort, bindInterface));
					connector.setHost(bindInterface);
				}
				if ((params.securePort < 1024) && !isWindows()) {
					logger.warn("Gitblit needs to run with ROOT permissions for ports < 1024!");
				}

				server.addConnector(connector);
			} else {
				logger.warn("Failed to find or load Keystore?");
				logger.warn("HTTPS transport DISABLED.");
			}
		}

		// conditionally configure the http transport
		if (params.port > 0) {
			/*
			 * HTTP
			 */
			logger.info("Setting up HTTP transport on port " + params.port);

			final HttpConfiguration httpConfig = new HttpConfiguration();
			if ((params.port > 0) && (params.securePort > 0)
					&& settings.getBoolean(Keys.server.redirectToHttpsPort, true)) {
				httpConfig.setSecureScheme("https");
				httpConfig.setSecurePort(params.securePort);
			}
			httpConfig.setSendServerVersion(false);
			httpConfig.setSendDateHeader(false);

			final ServerConnector connector = new ServerConnector(server,
					new HttpConnectionFactory(httpConfig));
			connector.setSoLingerTime(-1);
			connector.setIdleTimeout(30000);
			connector.setPort(params.port);
			final String bindInterface = settings.getString(Keys.server.httpBindInterface, null);
			if (!StringUtils.isEmpty(bindInterface)) {
				logger.warn(
						MessageFormat.format("Binding HTTP transport on port {0,number,0} to {1}",
								params.port, bindInterface));
				connector.setHost(bindInterface);
			}
			if ((params.port < 1024) && !isWindows()) {
				logger.warn("Gitblit needs to run with ROOT permissions for ports < 1024!");
			}

			server.addConnector(connector);
		}

		// tempDir is where the embedded Gitblit web application is expanded and
		// where Jetty creates any necessary temporary files
		final File tempDir = com.gitblit.utils.FileUtils.resolveParameter(Constants.baseFolder$,
				baseFolder, params.temp);
		if (tempDir.exists()) {
			try {
				FileUtils.delete(tempDir, FileUtils.RECURSIVE | FileUtils.RETRY);
			}
			catch (final IOException x) {
				logger.warn("Failed to delete temp dir " + tempDir.getAbsolutePath(), x);
			}
		}
		if (!tempDir.mkdirs()) {
			logger.warn("Failed to create temp dir " + tempDir.getAbsolutePath());
		}

		// Get the execution path of this class
		// We use this to set the WAR path.
		final ProtectionDomain protectionDomain = GitBlitServer.class.getProtectionDomain();
		final URL location = protectionDomain.getCodeSource().getLocation();

		// Root WebApp Context
		final WebAppContext rootContext = new WebAppContext();
		rootContext.setContextPath(settings.getString(Keys.server.contextPath, "/"));
		rootContext.setServer(server);
		rootContext.setWar(location.toExternalForm());
		rootContext.setTempDirectory(tempDir);

		// Set cookies HttpOnly so they are not accessible to JavaScript engines
		final HashSessionManager sessionManager = new HashSessionManager();
		sessionManager.setHttpOnly(true);
		// Use secure cookies if only serving https
		sessionManager.setSecureRequestOnly((params.port <= 0) && (params.securePort > 0));
		rootContext.getSessionHandler().setSessionManager(sessionManager);

		// Ensure there is a defined User Service
		final String realmUsers = params.userService;
		if (StringUtils.isEmpty(realmUsers)) {
			logger.error(MessageFormat.format("PLEASE SPECIFY {0}!!", Keys.realm.userService));
			return;
		}

		// Override settings from the command-line
		settings.overrideSetting(Keys.realm.userService, params.userService);
		settings.overrideSetting(Keys.git.repositoriesFolder, params.repositoriesFolder);
		settings.overrideSetting(Keys.git.daemonPort, params.gitPort);
		settings.overrideSetting(Keys.git.sshPort, params.sshPort);

		// Start up an in-memory LDAP server, if configured
		try {
			if (!StringUtils.isEmpty(params.ldapLdifFile)) {
				final File ldifFile = new File(params.ldapLdifFile);
				if (ldifFile.exists()) {
					final URI ldapUrl = new URI(settings.getRequiredString(Keys.realm.ldap.server));
					final Scanner scanner = new Scanner(ldifFile);
					final String firstLine = scanner.nextLine();
					scanner.close();
					final String rootDN = firstLine.substring(4);
					final String bindUserName = settings.getString(Keys.realm.ldap.username, "");
					final String bindPassword = settings.getString(Keys.realm.ldap.password, "");

					// Get the port
					int port = ldapUrl.getPort();
					if (port == -1) {
						port = 389;
					}

					final InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(
							rootDN);
					config.addAdditionalBindCredentials(bindUserName, bindPassword);
					config.setListenerConfigs(
							InMemoryListenerConfig.createLDAPConfig("default", port));
					config.setSchema(null);

					final InMemoryDirectoryServer ds = new InMemoryDirectoryServer(config);
					ds.importFromLDIF(true, new LDIFReader(ldifFile));
					ds.startListening();

					logger.info("LDAP Server started at ldap://localhost:" + port);
				}
			}
		}
		catch (final Exception e) {
			// Completely optional, just show a warning
			logger.warn("Unable to start LDAP server", e);
		}

		// Set the server's contexts
		server.setHandler(rootContext);

		// redirect HTTP requests to HTTPS
		if ((params.port > 0) && (params.securePort > 0)
				&& settings.getBoolean(Keys.server.redirectToHttpsPort, true)) {
			logger.info(String.format("Configuring automatic http(%1$s) -> https(%2$s) redirects",
					params.port, params.securePort));
			// Create the internal mechanisms to handle secure connections and
			// redirects
			final Constraint constraint = new Constraint();
			constraint.setDataConstraint(Constraint.DC_CONFIDENTIAL);

			final ConstraintMapping cm = new ConstraintMapping();
			cm.setConstraint(constraint);
			cm.setPathSpec("/*");

			final ConstraintSecurityHandler sh = new ConstraintSecurityHandler();
			sh.setConstraintMappings(new ConstraintMapping[] { cm });

			// Configure this context to use the Security Handler defined before
			rootContext.setHandler(sh);
		}

		// Setup the Gitblit context
		final GitblitContext gitblit = newGitblit(settings, baseFolder);
		rootContext.addEventListener(gitblit);

		try {
			// start the shutdown monitor
			if (params.shutdownPort > 0) {
				final Thread shutdownMonitor = new ShutdownMonitorThread(server, params);
				shutdownMonitor.start();
			}

			// start Jetty
			server.start();
			server.join();
		}
		catch (final Exception e) {
			e.printStackTrace();
			throw new RuntimeException("System.exit(100);");
		}
	}

	protected GitblitContext newGitblit(IStoredSettings settings, File baseFolder) {
		return new GitblitContext(settings, baseFolder);
	}

	/**
	 * Tests to see if the operating system is Windows.
	 *
	 * @return true if this is a windows machine
	 */
	private static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().indexOf("windows") > -1;
	}

	/**
	 * The ShutdownMonitorThread opens a socket on a specified port and waits
	 * for an incoming connection. When that connection is accepted a shutdown
	 * message is issued to the running Jetty server.
	 *
	 * @author James Moger
	 *
	 */
	private static class ShutdownMonitorThread extends Thread {

		private final ServerSocket socket;

		private final Server server;

		private final Logger logger = LoggerFactory.getLogger(ShutdownMonitorThread.class);

		public ShutdownMonitorThread(Server server, Params params) {
			this.server = server;
			setDaemon(true);
			setName(Constants.NAME + " Shutdown Monitor");
			ServerSocket skt = null;
			try {
				skt = new ServerSocket(params.shutdownPort, 1, InetAddress.getByName("127.0.0.1"));
			}
			catch (final Exception e) {
				this.logger.warn("Could not open shutdown monitor on port " + params.shutdownPort,
						e);
			}
			this.socket = skt;
		}

		@Override
		public void run() {
			// Only run if the socket was able to be created (not already in
			// use, failed to bind, etc.)
			if (null != this.socket) {
				this.logger
						.info("Shutdown Monitor listening on port " + this.socket.getLocalPort());
				Socket accept;
				try {
					accept = this.socket.accept();
					final BufferedReader reader = new BufferedReader(
							new InputStreamReader(accept.getInputStream()));
					reader.readLine();
					this.logger.info(Constants.BORDER);
					this.logger.info("Stopping " + Constants.NAME);
					this.logger.info(Constants.BORDER);
					this.server.stop();
					this.server.setStopAtShutdown(false);
					accept.close();
					this.socket.close();
				}
				catch (final Exception e) {
					this.logger.warn("Failed to shutdown Jetty", e);
				}
			}
		}
	}

	/**
	 * Parameters class for GitBlitServer.
	 */
	public static class Params {

		public static String baseFolder;

		private final FileSettings FILESETTINGS = new FileSettings(
				new File(baseFolder, Constants.PROPERTIES_FILE).getAbsolutePath());

		/*
		 * Server parameters
		 */
		@Option(name = "--help", aliases = { "-h" }, usage = "Show this help")
		public Boolean help = false;

		@Option(name = "--stop", usage = "Stop Server")
		public Boolean stop = false;

		@Option(name = "--tempFolder", usage = "Folder for server to extract built-in webapp", metaVar = "PATH")
		public String temp = this.FILESETTINGS.getString(Keys.server.tempFolder, "temp");

		@Option(name = "--dailyLogFile", usage = "Log to a rolling daily log file INSTEAD of stdout.")
		public Boolean dailyLogFile = false;

		/*
		 * GIT Servlet Parameters
		 */
		@Option(name = "--repositoriesFolder", usage = "Git Repositories Folder", metaVar = "PATH")
		public String repositoriesFolder = this.FILESETTINGS.getString(Keys.git.repositoriesFolder,
				"git");

		/*
		 * Authentication Parameters
		 */
		@Option(name = "--userService", usage = "Authentication and Authorization Service (filename or fully qualified classname)")
		public String userService = this.FILESETTINGS.getString(Keys.realm.userService,
				"users.conf");

		/*
		 * JETTY Parameters
		 */
		@Option(name = "--httpPort", usage = "HTTP port for to serve. (port <= 0 will disable this connector)", metaVar = "PORT")
		public Integer port = this.FILESETTINGS.getInteger(Keys.server.httpPort, 0);

		@Option(name = "--httpsPort", usage = "HTTPS port to serve.  (port <= 0 will disable this connector)", metaVar = "PORT")
		public Integer securePort = this.FILESETTINGS.getInteger(Keys.server.httpsPort, 8443);

		@Option(name = "--gitPort", usage = "Git Daemon port to serve.  (port <= 0 will disable this connector)", metaVar = "PORT")
		public Integer gitPort = this.FILESETTINGS.getInteger(Keys.git.daemonPort, 9418);

		@Option(name = "--sshPort", usage = "Git SSH port to serve.  (port <= 0 will disable this connector)", metaVar = "PORT")
		public Integer sshPort = this.FILESETTINGS.getInteger(Keys.git.sshPort, 29418);

		@Option(name = "--alias", usage = "Alias of SSL certificate in keystore for serving https.", metaVar = "ALIAS")
		public String alias = this.FILESETTINGS.getString(Keys.server.certificateAlias, "");

		@Option(name = "--storePassword", usage = "Password for SSL (https) keystore.", metaVar = "PASSWORD")
		public String storePassword = this.FILESETTINGS.getString(Keys.server.storePassword, "");

		@Option(name = "--shutdownPort", usage = "Port for Shutdown Monitor to listen on. (port <= 0 will disable this monitor)", metaVar = "PORT")
		public Integer shutdownPort = this.FILESETTINGS.getInteger(Keys.server.shutdownPort, 8081);

		@Option(name = "--requireClientCertificates", usage = "Require client X509 certificates for https connections.")
		public Boolean requireClientCertificates = this.FILESETTINGS
				.getBoolean(Keys.server.requireClientCertificates, false);

		/*
		 * Setting overrides
		 */
		@Option(name = "--settings", usage = "Path to alternative settings", metaVar = "FILE")
		public String settingsfile;

		@Option(name = "--ldapLdifFile", usage = "Path to LDIF file.  This will cause an in-memory LDAP server to be started according to gitblit settings", metaVar = "FILE")
		public String ldapLdifFile;

	}
}