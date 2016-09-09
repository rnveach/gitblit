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
package com.gitblit.servlet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.FileSettings;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.WebXmlSettings;
import com.gitblit.extensions.LifeCycleListener;
import com.gitblit.guice.CoreModule;
import com.gitblit.guice.WebModule;
import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.manager.IFederationManager;
import com.gitblit.manager.IFilestoreManager;
import com.gitblit.manager.IGitblit;
import com.gitblit.manager.IManager;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IPluginManager;
import com.gitblit.manager.IProjectManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IServicesManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.tickets.ITicketService;
import com.gitblit.transport.ssh.IPublicKeyManager;
import com.gitblit.utils.ContainerUtils;
import com.gitblit.utils.StringUtils;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceServletContextListener;

/**
 * This class is the main entry point for the entire webapp. It is a singleton
 * created manually by Gitblit GO or dynamically by the WAR/Express servlet
 * container. This class instantiates and starts all managers.
 *
 * Servlets and filters are injected which allows Gitblit to be completely
 * code-driven.
 *
 * @author James Moger
 *
 */
public class GitblitContext extends GuiceServletContextListener {

	private static GitblitContext gitblit;

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	private final List<IManager> managers = new ArrayList<IManager>();

	private final IStoredSettings goSettings;

	private final File goBaseFolder;

	/**
	 * Construct a Gitblit WAR/Express context.
	 */
	public GitblitContext() {
		this(null, null);
	}

	/**
	 * Construct a Gitblit GO context.
	 *
	 * @param settings
	 * @param baseFolder
	 */
	public GitblitContext(IStoredSettings settings, File baseFolder) {
		this.goSettings = settings;
		this.goBaseFolder = baseFolder;
		gitblit = this;
	}

	/**
	 * This method is only used for unit and integration testing.
	 *
	 * @param managerClass
	 * @return a manager
	 */
	@SuppressWarnings("unchecked")
	public static <X extends IManager> X getManager(Class<X> managerClass) {
		for (final IManager manager : gitblit.managers) {
			if (managerClass.isAssignableFrom(manager.getClass())) {
				return (X) manager;
			}
		}
		return null;
	}

	@Override
	protected Injector getInjector() {
		return Guice.createInjector(getModules());
	}

	/**
	 * Returns Gitblit's Guice injection modules.
	 */
	protected AbstractModule[] getModules() {
		return new AbstractModule[] { new CoreModule(), new WebModule() };
	}

	/**
	 * Configure Gitblit from the web.xml, if no configuration has already been
	 * specified.
	 *
	 * @see ServletContextListener.contextInitialize(ServletContextEvent)
	 */
	@Override
	public final void contextInitialized(ServletContextEvent contextEvent) {
		super.contextInitialized(contextEvent);

		final ServletContext context = contextEvent.getServletContext();
		startCore(context);
	}

	/**
	 * Prepare runtime settings and start all manager instances.
	 */
	protected void startCore(ServletContext context) {
		final Injector injector = (Injector) context.getAttribute(Injector.class.getName());

		// create the runtime settings object
		final IStoredSettings runtimeSettings = injector.getInstance(IStoredSettings.class);
		final File baseFolder;

		if (this.goSettings != null) {
			// Gitblit GO
			baseFolder = configureGO(context, this.goSettings, this.goBaseFolder, runtimeSettings);
		} else {
			// servlet container
			final WebXmlSettings webxmlSettings = new WebXmlSettings(context);
			final String contextRealPath = context.getRealPath("/");
			final File contextFolder = (contextRealPath != null) ? new File(contextRealPath) : null;

			// if the base folder dosen't match the default assume they don't
			// want to use express,
			// this allows for other containers to customise the basefolder per
			// context.
			final String defaultBase = Constants.contextFolder$ + "/WEB-INF/data";
			final String base = getBaseFolderPath(defaultBase);
			if (!StringUtils.isEmpty(System.getenv("OPENSHIFT_DATA_DIR"))
					&& defaultBase.equals(base)) {
				// RedHat OpenShift
				baseFolder = configureExpress(context, webxmlSettings, contextFolder,
						runtimeSettings);
			} else {
				// standard WAR
				baseFolder = configureWAR(context, webxmlSettings, contextFolder, runtimeSettings);
			}

			// Test for Tomcat forward-slash/%2F issue and auto-adjust settings
			ContainerUtils.CVE_2007_0450.test(runtimeSettings);
		}

		// Manually configure IRuntimeManager
		logManager(IRuntimeManager.class);
		final IRuntimeManager runtime = injector.getInstance(IRuntimeManager.class);
		runtime.setBaseFolder(baseFolder);
		runtime.getStatus().isGO = this.goSettings != null;
		runtime.getStatus().servletContainer = context.getServerInfo();
		runtime.start();
		this.managers.add(runtime);

		// create the plugin manager instance but do not start it
		loadManager(injector, IPluginManager.class);

		// start all other managers
		startManager(injector, INotificationManager.class);
		startManager(injector, IUserManager.class);
		startManager(injector, IAuthenticationManager.class);
		startManager(injector, IPublicKeyManager.class);
		startManager(injector, IRepositoryManager.class);
		startManager(injector, IProjectManager.class);
		startManager(injector, IFederationManager.class);
		startManager(injector, ITicketService.class);
		startManager(injector, IGitblit.class);
		startManager(injector, IServicesManager.class);
		startManager(injector, IFilestoreManager.class);

		// start the plugin manager last so that plugins can depend on
		// deterministic access to all other managers in their start() methods
		startManager(injector, IPluginManager.class);

		this.logger.info("");
		this.logger.info("All managers started.");
		this.logger.info("");

		final IPluginManager pluginManager = injector.getInstance(IPluginManager.class);
		for (final LifeCycleListener listener : pluginManager
				.getExtensions(LifeCycleListener.class)) {
			try {
				listener.onStartup();
			}
			catch (final Throwable t) {
				this.logger.error(null, t);
			}
		}
	}

	private String lookupBaseFolderFromJndi() {
		try {
			// try to lookup JNDI env-entry for the baseFolder
			final InitialContext ic = new InitialContext();
			final Context env = (Context) ic.lookup("java:comp/env");
			return (String) env.lookup("baseFolder");
		}
		catch (final NamingException n) {
			this.logger.error("Failed to get JNDI env-entry: " + n.getExplanation());
		}
		return null;
	}

	protected String getBaseFolderPath(String defaultBaseFolder) {
		// try a system property or a JNDI property
		String specifiedBaseFolder = System.getProperty("GITBLIT_HOME", lookupBaseFolderFromJndi());

		if (!StringUtils.isEmpty(System.getenv("GITBLIT_HOME"))) {
			// try an environment variable
			specifiedBaseFolder = System.getenv("GITBLIT_HOME");
		}

		if (!StringUtils.isEmpty(specifiedBaseFolder)) {
			// use specified base folder path
			return specifiedBaseFolder;
		}

		// use default base folder path
		return defaultBaseFolder;
	}

	protected <X extends IManager> X loadManager(Injector injector, Class<X> clazz) {
		final X x = injector.getInstance(clazz);
		return x;
	}

	protected <X extends IManager> X startManager(Injector injector, Class<X> clazz) {
		final X x = loadManager(injector, clazz);
		logManager(clazz);
		return startManager(x);
	}

	protected <X extends IManager> X startManager(X x) {
		x.start();
		this.managers.add(x);
		return x;
	}

	protected void logManager(Class<? extends IManager> clazz) {
		this.logger.info("");
		this.logger.info("----[{}]----", clazz.getName());
	}

	@Override
	public final void contextDestroyed(ServletContextEvent contextEvent) {
		super.contextDestroyed(contextEvent);
		final ServletContext context = contextEvent.getServletContext();
		destroyContext(context);
	}

	/**
	 * Gitblit is being shutdown either because the servlet container is
	 * shutting down or because the servlet container is re-deploying Gitblit.
	 */
	protected void destroyContext(ServletContext context) {
		this.logger.info("Gitblit context destroyed by servlet container.");

		final IPluginManager pluginManager = getManager(IPluginManager.class);
		for (final LifeCycleListener listener : pluginManager
				.getExtensions(LifeCycleListener.class)) {
			try {
				listener.onShutdown();
			}
			catch (final Throwable t) {
				this.logger.error(null, t);
			}
		}

		for (final IManager manager : this.managers) {
			this.logger.debug("stopping {}", manager.getClass().getSimpleName());
			manager.stop();
		}
	}

	/**
	 * Configures Gitblit GO
	 *
	 * @param context
	 * @param settings
	 * @param baseFolder
	 * @param runtimeSettings
	 * @return the base folder
	 */
	protected File configureGO(ServletContext context, IStoredSettings goSettings,
			File goBaseFolder, IStoredSettings runtimeSettings) {

		this.logger.debug("configuring Gitblit GO");

		// merge the stored settings into the runtime settings
		//
		// if runtimeSettings is also a FileSettings w/o a specified target
		// file,
		// the target file for runtimeSettings is set to "localSettings".
		runtimeSettings.merge(goSettings);
		final File base = goBaseFolder;
		return base;
	}

	/**
	 * Configures a standard WAR instance of Gitblit.
	 *
	 * @param context
	 * @param webxmlSettings
	 * @param contextFolder
	 * @param runtimeSettings
	 * @return the base folder
	 */
	protected File configureWAR(ServletContext context, WebXmlSettings webxmlSettings,
			File contextFolder, IStoredSettings runtimeSettings) {

		// Gitblit is running in a standard servlet container
		this.logger.debug("configuring Gitblit WAR");
		this.logger.info("WAR contextFolder is "
				+ ((contextFolder != null) ? contextFolder.getAbsolutePath() : "<empty>"));

		final String webXmlPath = webxmlSettings.getString(Constants.baseFolder,
				Constants.contextFolder$ + "/WEB-INF/data");

		if (webXmlPath.contains(Constants.contextFolder$) && (contextFolder == null)) {
			// warn about null contextFolder (issue-199)
			this.logger.error("");
			this.logger.error(MessageFormat.format(
					"\"{0}\" depends on \"{1}\" but \"{2}\" is returning NULL for \"{1}\"!",
					Constants.baseFolder, Constants.contextFolder$, context.getServerInfo()));
			this.logger.error(MessageFormat.format(
					"Please specify a non-parameterized path for <context-param> {0} in web.xml!!",
					Constants.baseFolder));
			this.logger
					.error(MessageFormat
							.format("OR configure your servlet container to specify a \"{0}\" parameter in the context configuration!!",
									Constants.baseFolder));
			this.logger.error("");
		}

		final String baseFolderPath = getBaseFolderPath(webXmlPath);

		final File baseFolder = com.gitblit.utils.FileUtils.resolveParameter(
				Constants.contextFolder$, contextFolder, baseFolderPath);
		baseFolder.mkdirs();

		// try to extract the data folder resource to the baseFolder
		extractResources(context, "/WEB-INF/data/", baseFolder);

		// delegate all config to baseFolder/gitblit.properties file
		final File localSettings = new File(baseFolder, "gitblit.properties");
		final FileSettings fileSettings = new FileSettings(localSettings.getAbsolutePath());

		// merge the stored settings into the runtime settings
		//
		// if runtimeSettings is also a FileSettings w/o a specified target
		// file,
		// the target file for runtimeSettings is set to "localSettings".
		runtimeSettings.merge(fileSettings);

		return baseFolder;
	}

	/**
	 * Configures an OpenShift instance of Gitblit.
	 *
	 * @param context
	 * @param webxmlSettings
	 * @param contextFolder
	 * @param runtimeSettings
	 * @return the base folder
	 */
	private File configureExpress(ServletContext context, WebXmlSettings webxmlSettings,
			File contextFolder, IStoredSettings runtimeSettings) {

		// Gitblit is running in OpenShift/JBoss
		this.logger.debug("configuring Gitblit Express");
		final String openShift = System.getenv("OPENSHIFT_DATA_DIR");
		final File base = new File(openShift);
		this.logger.info("EXPRESS contextFolder is " + contextFolder.getAbsolutePath());

		// Copy the included scripts to the configured groovy folder
		final String path = webxmlSettings.getString(Keys.groovy.scriptsFolder, "groovy");
		final File localScripts = com.gitblit.utils.FileUtils.resolveParameter(
				Constants.baseFolder$, base, path);
		if (!localScripts.exists()) {
			final File warScripts = new File(contextFolder, "/WEB-INF/data/groovy");
			if (!warScripts.equals(localScripts)) {
				try {
					com.gitblit.utils.FileUtils.copy(localScripts, warScripts.listFiles());
				}
				catch (final IOException e) {
					this.logger.error(MessageFormat.format(
							"Failed to copy included Groovy scripts from {0} to {1}", warScripts,
							localScripts));
				}
			}
		}

		// Copy the included gitignore files to the configured gitignore folder
		final String gitignorePath = webxmlSettings
				.getString(Keys.git.gitignoreFolder, "gitignore");
		final File localGitignores = com.gitblit.utils.FileUtils.resolveParameter(
				Constants.baseFolder$, base, gitignorePath);
		if (!localGitignores.exists()) {
			final File warGitignores = new File(contextFolder, "/WEB-INF/data/gitignore");
			if (!warGitignores.equals(localGitignores)) {
				try {
					com.gitblit.utils.FileUtils.copy(localGitignores, warGitignores.listFiles());
				}
				catch (final IOException e) {
					this.logger.error(MessageFormat.format(
							"Failed to copy included .gitignore files from {0} to {1}",
							warGitignores, localGitignores));
				}
			}
		}

		// merge the WebXmlSettings into the runtime settings (for
		// backwards-compatibilty)
		runtimeSettings.merge(webxmlSettings);

		// settings are to be stored in openshift/gitblit.properties
		final File localSettings = new File(base, "gitblit.properties");
		final FileSettings fileSettings = new FileSettings(localSettings.getAbsolutePath());

		// merge the stored settings into the runtime settings
		//
		// if runtimeSettings is also a FileSettings w/o a specified target
		// file,
		// the target file for runtimeSettings is set to "localSettings".
		runtimeSettings.merge(fileSettings);

		return base;
	}

	protected void extractResources(ServletContext context, String path, File toDir) {
		final Set<String> resources = context.getResourcePaths(path);
		if (resources == null) {
			this.logger.warn("There are no WAR resources to extract from {}", path);
			return;
		}
		for (final String resource : resources) {
			// extract the resource to the directory if it does not exist
			final File f = new File(toDir, resource.substring(path.length()));
			if (!f.exists()) {
				InputStream is = null;
				OutputStream os = null;
				try {
					if (resource.charAt(resource.length() - 1) == '/') {
						// directory
						f.mkdirs();
						extractResources(context, resource, f);
					} else {
						// file
						f.getParentFile().mkdirs();
						is = context.getResourceAsStream(resource);
						os = new FileOutputStream(f);
						final byte[] buffer = new byte[4096];
						int len = 0;
						while ((len = is.read(buffer)) > -1) {
							os.write(buffer, 0, len);
						}
					}
				}
				catch (final FileNotFoundException e) {
					this.logger.error("Failed to find resource \"" + resource + "\"", e);
				}
				catch (final IOException e) {
					this.logger.error("Failed to copy resource \"" + resource + "\" to " + f, e);
				}
				finally {
					if (is != null) {
						try {
							is.close();
						}
						catch (final IOException e) {
							// ignore
						}
					}
					if (os != null) {
						try {
							os.close();
						}
						catch (final IOException e) {
							// ignore
						}
					}
				}
			}
		}
	}
}
