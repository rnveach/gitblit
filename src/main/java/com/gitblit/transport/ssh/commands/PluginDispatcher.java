/*
 * Copyright 2014 gitblit.com.
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
package com.gitblit.transport.ssh.commands;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import ro.fortsoft.pf4j.ExtensionPoint;
import ro.fortsoft.pf4j.PluginDependency;
import ro.fortsoft.pf4j.PluginDescriptor;
import ro.fortsoft.pf4j.PluginState;
import ro.fortsoft.pf4j.PluginWrapper;
import ro.fortsoft.pf4j.Version;

import com.gitblit.manager.IGitblit;
import com.gitblit.models.PluginRegistry.InstallState;
import com.gitblit.models.PluginRegistry.PluginRegistration;
import com.gitblit.models.PluginRegistry.PluginRelease;
import com.gitblit.utils.FlipTable;
import com.gitblit.utils.FlipTable.Borders;
import com.gitblit.utils.StringUtils;
import com.google.common.base.Joiner;

/**
 * The plugin dispatcher and commands for runtime plugin management.
 *
 * @author James Moger
 *
 */
@CommandMetaData(name = "plugin", description = "Plugin management commands", admin = true)
public class PluginDispatcher extends DispatchCommand {

	@Override
	protected void setup() {
		register(ListPlugins.class);
		register(StartPlugin.class);
		register(StopPlugin.class);
		register(EnablePlugin.class);
		register(DisablePlugin.class);
		register(ShowPlugin.class);
		register(RefreshPlugins.class);
		register(AvailablePlugins.class);
		register(InstallPlugin.class);
		register(UpgradePlugin.class);
		register(UninstallPlugin.class);
	}

	@CommandMetaData(name = "list", aliases = { "ls" }, description = "List plugins")
	public static class ListPlugins extends ListCommand<PluginWrapper> {

		@Override
		protected List<PluginWrapper> getItems() throws UnloggedFailure {
			final IGitblit gitblit = getContext().getGitblit();
			final List<PluginWrapper> list = gitblit.getPlugins();
			return list;
		}

		@Override
		protected void asTable(List<PluginWrapper> list) {
			String[] headers;
			if (this.verbose) {
				final String[] h = { "#", "Id", "Description", "Version", "Requires", "State",
						"Path" };
				headers = h;
			} else {
				final String[] h = { "#", "Id", "Version", "State", "Path" };
				headers = h;
			}
			final Object[][] data = new Object[list.size()][];
			for (int i = 0; i < list.size(); i++) {
				final PluginWrapper p = list.get(i);
				final PluginDescriptor d = p.getDescriptor();
				if (this.verbose) {
					data[i] = new Object[] { "" + (i + 1), d.getPluginId(),
							d.getPluginDescription(), d.getVersion(), d.getRequires(),
							p.getPluginState(), p.getPluginPath() };
				} else {
					data[i] = new Object[] { "" + (i + 1), d.getPluginId(), d.getVersion(),
							p.getPluginState(), p.getPluginPath() };
				}
			}

			this.stdout.println(FlipTable.of(headers, data, Borders.BODY_HCOLS));
		}

		@Override
		protected void asTabbed(List<PluginWrapper> list) {
			for (final PluginWrapper pw : list) {
				final PluginDescriptor d = pw.getDescriptor();
				if (this.verbose) {
					outTabbed(d.getPluginId(), d.getPluginDescription(), d.getVersion(),
							d.getRequires(), pw.getPluginState(), pw.getPluginPath());
				} else {
					outTabbed(d.getPluginId(), d.getVersion(), pw.getPluginState(),
							pw.getPluginPath());
				}
			}
		}
	}

	static abstract class PluginCommand extends SshCommand {

		protected PluginWrapper getPlugin(String id) throws Failure {
			final IGitblit gitblit = getContext().getGitblit();
			PluginWrapper pluginWrapper = null;
			try {
				final int index = Integer.parseInt(id);
				final List<PluginWrapper> plugins = gitblit.getPlugins();
				if (index > plugins.size()) {
					throw new UnloggedFailure(1, "Invalid plugin index specified!");
				}
				pluginWrapper = plugins.get(index - 1);
			}
			catch (final NumberFormatException e) {
				pluginWrapper = gitblit.getPlugin(id);
				if (pluginWrapper == null) {
					final PluginRegistration reg = gitblit.lookupPlugin(id);
					if (reg == null) {
						throw new UnloggedFailure("Invalid plugin specified!");
					}
					pluginWrapper = gitblit.getPlugin(reg.id);
				}
			}

			return pluginWrapper;
		}
	}

	@CommandMetaData(name = "start", description = "Start a plugin")
	public static class StartPlugin extends PluginCommand {

		@Argument(index = 0, required = true, metaVar = "ALL|<ID>|<INDEX>", usage = "the plugin to start")
		protected String id;

		@Override
		public void run() throws Failure {
			final IGitblit gitblit = getContext().getGitblit();
			if (this.id.equalsIgnoreCase("ALL")) {
				gitblit.startPlugins();
				this.stdout.println("All plugins started");
			} else {
				final PluginWrapper pluginWrapper = getPlugin(this.id);
				if (pluginWrapper == null) {
					throw new UnloggedFailure(String.format("Plugin %s is not installed!", this.id));
				}

				final PluginState state = gitblit.startPlugin(pluginWrapper.getPluginId());
				if (PluginState.STARTED.equals(state)) {
					this.stdout.println(String.format("Started %s", pluginWrapper.getPluginId()));
				} else {
					throw new UnloggedFailure(1, String.format("Failed to start %s",
							pluginWrapper.getPluginId()));
				}
			}
		}
	}

	@CommandMetaData(name = "stop", description = "Stop a plugin")
	public static class StopPlugin extends PluginCommand {

		@Argument(index = 0, required = true, metaVar = "ALL|<ID>|<INDEX>", usage = "the plugin to stop")
		protected String id;

		@Override
		public void run() throws Failure {
			final IGitblit gitblit = getContext().getGitblit();
			if (this.id.equalsIgnoreCase("ALL")) {
				gitblit.stopPlugins();
				this.stdout.println("All plugins stopped");
			} else {
				final PluginWrapper pluginWrapper = getPlugin(this.id);
				if (pluginWrapper == null) {
					throw new UnloggedFailure(String.format("Plugin %s is not installed!", this.id));
				}

				final PluginState state = gitblit.stopPlugin(pluginWrapper.getPluginId());
				if (PluginState.STOPPED.equals(state)) {
					this.stdout.println(String.format("Stopped %s", pluginWrapper.getPluginId()));
				} else {
					throw new UnloggedFailure(1, String.format("Failed to stop %s",
							pluginWrapper.getPluginId()));
				}
			}
		}
	}

	@CommandMetaData(name = "enable", description = "Enable a plugin")
	public static class EnablePlugin extends PluginCommand {

		@Argument(index = 0, required = true, metaVar = "<ID>|<INDEX>", usage = "the plugin to enable")
		protected String id;

		@Override
		public void run() throws Failure {
			final IGitblit gitblit = getContext().getGitblit();
			final PluginWrapper pluginWrapper = getPlugin(this.id);
			if (pluginWrapper == null) {
				throw new UnloggedFailure("Invalid plugin specified!");
			}

			if (gitblit.enablePlugin(pluginWrapper.getPluginId())) {
				this.stdout.println(String.format("Enabled %s", pluginWrapper.getPluginId()));
			} else {
				throw new UnloggedFailure(1, String.format("Failed to enable %s",
						pluginWrapper.getPluginId()));
			}
		}
	}

	@CommandMetaData(name = "disable", description = "Disable a plugin")
	public static class DisablePlugin extends PluginCommand {

		@Argument(index = 0, required = true, metaVar = "<ID>|<INDEX>", usage = "the plugin to disable")
		protected String id;

		@Override
		public void run() throws Failure {
			final IGitblit gitblit = getContext().getGitblit();
			final PluginWrapper pluginWrapper = getPlugin(this.id);
			if (pluginWrapper == null) {
				throw new UnloggedFailure("Invalid plugin specified!");
			}

			if (gitblit.disablePlugin(pluginWrapper.getPluginId())) {
				this.stdout.println(String.format("Disabled %s", pluginWrapper.getPluginId()));
			} else {
				throw new UnloggedFailure(1, String.format("Failed to disable %s",
						pluginWrapper.getPluginId()));
			}
		}
	}

	@CommandMetaData(name = "show", description = "Show the details of a plugin")
	public static class ShowPlugin extends PluginCommand {

		@Argument(index = 0, required = true, metaVar = "<ID>|<INDEX>", usage = "the plugin to show")
		protected String id;

		@Override
		public void run() throws Failure {
			final IGitblit gitblit = getContext().getGitblit();
			final PluginWrapper pw = getPlugin(this.id);
			if (pw == null) {
				final PluginRegistration registration = gitblit.lookupPlugin(this.id);
				if (registration == null) {
					throw new Failure(1, String.format("Unknown plugin %s", this.id));
				}
				show(registration);
			} else {
				show(pw);
			}
		}

		protected String buildFieldTable(PluginWrapper pw, PluginRegistration reg) {
			final Version system = getContext().getGitblit().getSystemVersion();
			PluginRelease current = reg == null ? null : reg.getCurrentRelease(system);
			if (current == null) {
				current = new PluginRelease();
				current.version = "";
				current.requires = "";
			}

			final String id = pw == null ? reg.id : pw.getPluginId();
			final String description = reg == null ? pw.getDescriptor().getPluginDescription()
					: reg.description;
			final String version = pw == null ? current.version : pw.getDescriptor().getVersion()
					.toString();
			final String requires = pw == null ? current.requires : pw.getDescriptor()
					.getRequires().toString();
			final String provider = pw == null ? reg.provider : pw.getDescriptor().getProvider();
			final String registry = reg == null ? "" : reg.registry;
			final String path = pw == null ? "" : pw.getPluginPath();
			final String projectUrl = reg == null ? "" : reg.projectUrl;
			final String state;
			if (pw == null) {
				// plugin could be installed
				state = InstallState.NOT_INSTALLED.toString();
			} else if (reg == null) {
				// unregistered, installed plugin
				state = Joiner.on(", ").join(InstallState.INSTALLED, pw.getPluginState());
			} else {
				// registered, installed plugin
				state = Joiner.on(", ").join(reg.getInstallState(system), pw.getPluginState());
			}

			final StringBuilder sb = new StringBuilder();
			sb.append("ID          : ").append(id).append('\n');
			sb.append("Version     : ").append(version).append('\n');
			sb.append("Requires    : ").append(requires).append('\n');
			sb.append("State       : ").append(state).append('\n');
			sb.append("Path        : ").append(path).append('\n');
			sb.append('\n');
			sb.append("Description : ").append(description).append('\n');
			sb.append("Provider    : ").append(provider).append('\n');
			sb.append("Project URL : ").append(projectUrl).append('\n');
			sb.append("Registry    : ").append(registry).append('\n');

			return sb.toString();
		}

		protected String buildReleaseTable(PluginRegistration reg) {
			final List<PluginRelease> releases = reg.releases;
			Collections.sort(releases);
			String releaseTable;
			if (releases.isEmpty()) {
				releaseTable = FlipTable.EMPTY;
			} else {
				final String[] headers = { "Version", "Date", "Requires" };
				final Object[][] data = new Object[releases.size()][];
				for (int i = 0; i < releases.size(); i++) {
					final PluginRelease release = releases.get(i);
					data[i] = new Object[] {
							(release.version.equals(reg.installedRelease) ? ">" : " ")
									+ release.version, release.date, release.requires };
				}
				releaseTable = FlipTable.of(headers, data, Borders.COLS);
			}
			return releaseTable;
		}

		/**
		 * Show an uninstalled plugin.
		 *
		 * @param reg
		 */
		protected void show(PluginRegistration reg) {
			// REGISTRATION
			final String fields = buildFieldTable(null, reg);
			final String releases = buildReleaseTable(reg);

			final String[] headers = { reg.id };
			final Object[][] data = new Object[3][];
			data[0] = new Object[] { fields };
			data[1] = new Object[] { "RELEASES" };
			data[2] = new Object[] { releases };
			this.stdout.println(FlipTable.of(headers, data));
		}

		/**
		 * Show an installed plugin.
		 *
		 * @param pw
		 */
		protected void show(PluginWrapper pw) {
			final IGitblit gitblit = getContext().getGitblit();
			final PluginRegistration reg = gitblit.lookupPlugin(pw.getPluginId());

			// FIELDS
			final String fields = buildFieldTable(pw, reg);

			// EXTENSIONS
			final StringBuilder sb = new StringBuilder();
			final List<Class<?>> exts = gitblit.getExtensionClasses(pw.getPluginId());
			String extensions;
			if (exts.isEmpty()) {
				extensions = FlipTable.EMPTY;
			} else {
				final StringBuilder description = new StringBuilder();
				for (int i = 0; i < exts.size(); i++) {
					final Class<?> ext = exts.get(i);
					if (ext.isAnnotationPresent(CommandMetaData.class)) {
						final CommandMetaData meta = ext.getAnnotation(CommandMetaData.class);
						description.append(meta.name());
						if (meta.description().length() > 0) {
							description.append(": ").append(meta.description());
						}
						description.append('\n');
					}
					description.append(ext.getName()).append("\n  └ ");
					description.append(getExtensionPoint(ext).getName());
					description.append("\n\n");
				}
				extensions = description.toString();
			}

			// DEPENDENCIES
			sb.setLength(0);
			final List<PluginDependency> deps = pw.getDescriptor().getDependencies();
			String dependencies;
			if (deps.isEmpty()) {
				dependencies = FlipTable.EMPTY;
			} else {
				final String[] headers = { "Id", "Version" };
				final Object[][] data = new Object[deps.size()][];
				for (int i = 0; i < deps.size(); i++) {
					final PluginDependency dep = deps.get(i);
					data[i] = new Object[] { dep.getPluginId(), dep.getPluginVersion() };
				}
				dependencies = FlipTable.of(headers, data, Borders.COLS);
			}

			// RELEASES
			String releases;
			if (reg == null) {
				releases = FlipTable.EMPTY;
			} else {
				releases = buildReleaseTable(reg);
			}

			final String[] headers = { pw.getPluginId() };
			final Object[][] data = new Object[7][];
			data[0] = new Object[] { fields };
			data[1] = new Object[] { "EXTENSIONS" };
			data[2] = new Object[] { extensions };
			data[3] = new Object[] { "DEPENDENCIES" };
			data[4] = new Object[] { dependencies };
			data[5] = new Object[] { "RELEASES" };
			data[6] = new Object[] { releases };
			this.stdout.println(FlipTable.of(headers, data));
		}

		/* Find the ExtensionPoint */
		protected Class<?> getExtensionPoint(Class<?> clazz) {
			final Class<?> superClass = clazz.getSuperclass();
			if (ExtensionPoint.class.isAssignableFrom(superClass)) {
				return superClass;
			}
			return getExtensionPoint(superClass);
		}
	}

	@CommandMetaData(name = "refresh", description = "Refresh the plugin registry data")
	public static class RefreshPlugins extends SshCommand {

		@Option(name = "--noverify", usage = "Disable checksum verification")
		private boolean disableChecksum;

		@Override
		public void run() throws Failure {
			final IGitblit gitblit = getContext().getGitblit();
			gitblit.refreshRegistry(!this.disableChecksum);
		}
	}

	@CommandMetaData(name = "available", description = "List the available plugins")
	public static class AvailablePlugins extends ListFilterCommand<PluginRegistration> {

		@Option(name = "--refresh", aliases = { "-r" }, usage = "refresh the plugin registry")
		protected boolean refresh;

		@Option(name = "--updates", aliases = { "-u" }, usage = "show available updates")
		protected boolean updates;

		@Option(name = "--noverify", usage = "Disable checksum verification")
		private boolean disableChecksum;

		@Override
		protected List<PluginRegistration> getItems() throws UnloggedFailure {
			final IGitblit gitblit = getContext().getGitblit();
			if (this.refresh) {
				gitblit.refreshRegistry(!this.disableChecksum);
			}

			List<PluginRegistration> list;
			if (this.updates) {
				list = gitblit.getRegisteredPlugins(InstallState.UPDATE_AVAILABLE);
			} else {
				list = gitblit.getRegisteredPlugins();
			}
			return list;
		}

		@Override
		protected boolean matches(String filter, PluginRegistration t) {
			return t.id.matches(filter)
					|| ((t.description != null) && t.description.matches(filter));
		}

		@Override
		protected void asTable(List<PluginRegistration> list) {
			String[] headers;
			if (this.verbose) {
				final String[] h = { "Id", "Description", "Installed", "Current", "Requires",
						"State", "Registry" };
				headers = h;
			} else {
				final String[] h = { "Id", "Installed", "Current", "Requires", "State" };
				headers = h;
			}
			final Version system = getContext().getGitblit().getSystemVersion();
			final Object[][] data = new Object[list.size()][];
			for (int i = 0; i < list.size(); i++) {
				final PluginRegistration p = list.get(i);
				PluginRelease curr = p.getCurrentRelease(system);
				if (curr == null) {
					curr = new PluginRelease();
				}
				if (this.verbose) {
					data[i] = new Object[] { p.id, p.description, p.installedRelease, curr.version,
							curr.requires, p.getInstallState(system), p.registry };
				} else {
					data[i] = new Object[] { p.id, p.installedRelease, curr.version, curr.requires,
							p.getInstallState(system) };
				}
			}

			this.stdout.println(FlipTable.of(headers, data, Borders.BODY_HCOLS));
		}

		@Override
		protected void asTabbed(List<PluginRegistration> list) {
			final Version system = getContext().getGitblit().getSystemVersion();
			for (final PluginRegistration p : list) {
				PluginRelease curr = p.getCurrentRelease(system);
				if (curr == null) {
					curr = new PluginRelease();
				}
				if (this.verbose) {
					outTabbed(p.id, p.description, p.installedRelease, curr.version, curr.requires,
							p.getInstallState(system), p.provider, p.registry);
				} else {
					outTabbed(p.id, p.installedRelease, curr.version, curr.requires,
							p.getInstallState(system));
				}
			}
		}
	}

	@CommandMetaData(name = "install", description = "Download and installs a plugin")
	public static class InstallPlugin extends SshCommand {

		@Argument(index = 0, required = true, metaVar = "<URL>|<ID>", usage = "the id or the url of the plugin to download and install")
		protected String urlOrId;

		@Option(name = "--version", usage = "The specific version to install")
		private String version;

		@Option(name = "--noverify", usage = "Disable checksum verification")
		private boolean disableChecksum;

		@Override
		public void run() throws Failure {
			final IGitblit gitblit = getContext().getGitblit();
			try {
				final String ulc = this.urlOrId.toLowerCase();
				if (ulc.startsWith("http://") || ulc.startsWith("https://")) {
					if (gitblit.installPlugin(this.urlOrId, !this.disableChecksum)) {
						this.stdout.println(String.format("Installed %s", this.urlOrId));
					} else {
						throw new UnloggedFailure(1, String.format("Failed to install %s",
								this.urlOrId));
					}
				} else {
					final PluginRelease pr = gitblit.lookupRelease(this.urlOrId, this.version);
					if (pr == null) {
						throw new UnloggedFailure(1, String.format(
								"Plugin \"%s\" is not in the registry!", this.urlOrId));
					}

					// enforce minimum system requirement
					if (!StringUtils.isEmpty(pr.requires)) {
						final Version requires = Version.createVersion(pr.requires);
						final Version system = gitblit.getSystemVersion();
						final boolean isValid = system.isZero() || system.atLeast(requires);
						if (!isValid) {
							final String msg = String.format(
									"Plugin \"%s:%s\" requires Gitblit %s", this.urlOrId,
									pr.version, pr.requires);
							throw new UnloggedFailure(1, msg);
						}
					}

					if (gitblit.installPlugin(pr.url, !this.disableChecksum)) {
						this.stdout.println(String.format("Installed %s", this.urlOrId));
					} else {
						throw new UnloggedFailure(1, String.format("Failed to install %s",
								this.urlOrId));
					}
				}
			}
			catch (final IOException e) {
				this.log.error("Failed to install " + this.urlOrId, e);
				throw new UnloggedFailure(1, String.format("Failed to install %s", this.urlOrId), e);
			}
		}
	}

	@CommandMetaData(name = "upgrade", description = "Upgrade a plugin")
	public static class UpgradePlugin extends PluginCommand {

		@Argument(index = 0, required = true, metaVar = "<ID>|<INDEX>", usage = "the plugin to upgrade")
		protected String id;

		@Option(name = "--version", usage = "The specific version to install")
		private String version;

		@Option(name = "--noverify", usage = "Disable checksum verification")
		private boolean disableChecksum;

		@Override
		public void run() throws Failure {
			final IGitblit gitblit = getContext().getGitblit();
			final PluginWrapper pluginWrapper = getPlugin(this.id);
			if (pluginWrapper == null) {
				throw new UnloggedFailure("Invalid plugin specified!");
			}

			final PluginRelease pr = gitblit.lookupRelease(pluginWrapper.getPluginId(),
					this.version);
			if (pr == null) {
				throw new UnloggedFailure(1, String.format("Plugin \"%s\" is not in the registry!",
						pluginWrapper.getPluginId()));
			}

			// enforce minimum system requirement
			if (!StringUtils.isEmpty(pr.requires)) {
				final Version requires = Version.createVersion(pr.requires);
				final Version system = gitblit.getSystemVersion();
				final boolean isValid = system.isZero() || system.atLeast(requires);
				if (!isValid) {
					throw new Failure(1, String.format("Plugin \"%s:%s\" requires Gitblit %s",
							pluginWrapper.getPluginId(), pr.version, pr.requires));
				}
			}

			try {
				if (gitblit.upgradePlugin(pluginWrapper.getPluginId(), pr.url,
						!this.disableChecksum)) {
					this.stdout.println(String.format("Upgraded %s", pluginWrapper.getPluginId()));
				} else {
					throw new UnloggedFailure(1, String.format("Failed to upgrade %s",
							pluginWrapper.getPluginId()));
				}
			}
			catch (final IOException e) {
				this.log.error("Failed to upgrade " + pluginWrapper.getPluginId(), e);
				throw new UnloggedFailure(1, String.format("Failed to upgrade %s",
						pluginWrapper.getPluginId()), e);
			}
		}
	}

	@CommandMetaData(name = "uninstall", aliases = { "rm", "del" }, description = "Uninstall a plugin")
	public static class UninstallPlugin extends PluginCommand {

		@Argument(index = 0, required = true, metaVar = "<ID>|<INDEX>", usage = "the plugin to uninstall")
		protected String id;

		@Override
		public void run() throws Failure {
			final IGitblit gitblit = getContext().getGitblit();
			final PluginWrapper pluginWrapper = getPlugin(this.id);
			if (pluginWrapper == null) {
				throw new UnloggedFailure(String.format("Plugin %s is not installed!", this.id));
			}

			if (gitblit.uninstallPlugin(pluginWrapper.getPluginId())) {
				this.stdout.println(String.format("Uninstalled %s", pluginWrapper.getPluginId()));
			} else {
				throw new UnloggedFailure(1, String.format("Failed to uninstall %s",
						pluginWrapper.getPluginId()));
			}
		}
	}
}
