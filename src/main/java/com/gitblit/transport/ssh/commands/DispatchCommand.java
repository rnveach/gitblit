/*
 * Copyright (C) 2009 The Android Open Source Project
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
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.fortsoft.pf4j.ExtensionPoint;

import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.cli.SubcommandHandler;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;

/**
 * Parses an SSH command-line and dispatches the command to the appropriate
 * BaseCommand instance.
 *
 * @since 1.5.0
 */
public abstract class DispatchCommand extends BaseCommand implements ExtensionPoint {

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Argument(index = 0, required = false, metaVar = "COMMAND", handler = SubcommandHandler.class)
	private String commandName;

	@Argument(index = 1, multiValued = true, metaVar = "ARG")
	private final List<String> args = new ArrayList<String>();

	private final Set<Class<? extends BaseCommand>> commands;
	private final Map<String, DispatchCommand> dispatchers;
	private final Map<String, String> aliasToCommand;
	private final Map<String, List<String>> commandToAliases;
	private final List<BaseCommand> instantiated;
	private Map<String, Class<? extends BaseCommand>> map;

	protected DispatchCommand() {
		this.commands = new HashSet<Class<? extends BaseCommand>>();
		this.dispatchers = Maps.newHashMap();
		this.aliasToCommand = Maps.newHashMap();
		this.commandToAliases = Maps.newHashMap();
		this.instantiated = new ArrayList<BaseCommand>();
	}

	@Override
	public void destroy() {
		super.destroy();
		this.commands.clear();
		this.aliasToCommand.clear();
		this.commandToAliases.clear();
		this.map = null;

		for (final BaseCommand command : this.instantiated) {
			command.destroy();
		}
		this.instantiated.clear();

		for (final DispatchCommand dispatcher : this.dispatchers.values()) {
			dispatcher.destroy();
		}
		this.dispatchers.clear();
	}

	/**
	 * Setup this dispatcher. Commands and nested dispatchers are normally
	 * registered within this method.
	 *
	 * @since 1.5.0
	 */
	protected abstract void setup();

	/**
	 * Register a command or a dispatcher by it's class.
	 *
	 * @param clazz
	 */
	@SuppressWarnings("unchecked")
	protected final void register(Class<? extends BaseCommand> clazz) {
		if (DispatchCommand.class.isAssignableFrom(clazz)) {
			registerDispatcher((Class<? extends DispatchCommand>) clazz);
			return;
		}

		registerCommand(clazz);
	}

	/**
	 * Register a command or a dispatcher instance.
	 *
	 * @param cmd
	 */
	protected final void register(BaseCommand cmd) {
		if (cmd instanceof DispatchCommand) {
			registerDispatcher((DispatchCommand) cmd);
			return;
		}
		registerCommand(cmd);
	}

	private void registerDispatcher(Class<? extends DispatchCommand> clazz) {
		try {
			final DispatchCommand dispatcher = clazz.newInstance();
			registerDispatcher(dispatcher);
		}
		catch (final Exception e) {
			this.log.error("failed to instantiate {}", clazz.getName());
		}
	}

	private void registerDispatcher(DispatchCommand dispatcher) {
		final Class<? extends DispatchCommand> dispatcherClass = dispatcher.getClass();
		if (!dispatcherClass.isAnnotationPresent(CommandMetaData.class)) {
			throw new RuntimeException(MessageFormat.format("{0} must be annotated with {1}!",
					dispatcher.getName(), CommandMetaData.class.getName()));
		}

		final UserModel user = getContext().getClient().getUser();
		final CommandMetaData meta = dispatcherClass.getAnnotation(CommandMetaData.class);
		if (meta.admin() && !user.canAdmin()) {
			this.log.debug(MessageFormat.format("excluding admin dispatcher {0} for {1}",
					meta.name(), user.username));
			return;
		}

		try {
			dispatcher.setContext(getContext());
			dispatcher.setWorkQueue(getWorkQueue());
			dispatcher.setup();
			if (dispatcher.commands.isEmpty() && dispatcher.dispatchers.isEmpty()) {
				this.log.debug(MessageFormat.format("excluding empty dispatcher {0} for {1}",
						meta.name(), user.username));
				return;
			}

			this.log.debug("registering {} dispatcher", meta.name());
			this.dispatchers.put(meta.name(), dispatcher);
			for (final String alias : meta.aliases()) {
				this.aliasToCommand.put(alias, meta.name());
				if (!this.commandToAliases.containsKey(meta.name())) {
					this.commandToAliases.put(meta.name(), new ArrayList<String>());
				}
				this.commandToAliases.get(meta.name()).add(alias);
			}
		}
		catch (final Exception e) {
			this.log.error("failed to register {} dispatcher", meta.name());
		}
	}

	/**
	 * Registers a command as long as the user is permitted to execute it.
	 *
	 * @param clazz
	 */
	private void registerCommand(Class<? extends BaseCommand> clazz) {
		if (!clazz.isAnnotationPresent(CommandMetaData.class)) {
			throw new RuntimeException(MessageFormat.format("{0} must be annotated with {1}!",
					clazz.getName(), CommandMetaData.class.getName()));
		}

		final UserModel user = getContext().getClient().getUser();
		final CommandMetaData meta = clazz.getAnnotation(CommandMetaData.class);
		if (meta.admin() && !user.canAdmin()) {
			this.log.debug(MessageFormat.format("excluding admin command {0} for {1}", meta.name(),
					user.username));
			return;
		}
		this.commands.add(clazz);
	}

	/**
	 * Registers a command as long as the user is permitted to execute it.
	 *
	 * @param cmd
	 */
	private void registerCommand(BaseCommand cmd) {
		if (!cmd.getClass().isAnnotationPresent(CommandMetaData.class)) {
			throw new RuntimeException(MessageFormat.format("{0} must be annotated with {1}!",
					cmd.getName(), CommandMetaData.class.getName()));
		}

		final UserModel user = getContext().getClient().getUser();
		final CommandMetaData meta = cmd.getClass().getAnnotation(CommandMetaData.class);
		if (meta.admin() && !user.canAdmin()) {
			this.log.debug(MessageFormat.format("excluding admin command {0} for {1}", meta.name(),
					user.username));
			return;
		}
		this.commands.add(cmd.getClass());
		this.instantiated.add(cmd);
	}

	private Map<String, Class<? extends BaseCommand>> getMap() {
		if (this.map == null) {
			this.map = Maps.newHashMapWithExpectedSize(this.commands.size());
			for (final Class<? extends BaseCommand> cmd : this.commands) {
				final CommandMetaData meta = cmd.getAnnotation(CommandMetaData.class);
				if (this.map.containsKey(meta.name())
						|| this.aliasToCommand.containsKey(meta.name())) {
					this.log.warn("{} already contains the \"{}\" command!", getName(), meta.name());
				} else {
					this.map.put(meta.name(), cmd);
				}
				for (final String alias : meta.aliases()) {
					if (this.map.containsKey(alias) || this.aliasToCommand.containsKey(alias)) {
						this.log.warn("{} already contains the \"{}\" command!", getName(), alias);
					} else {
						this.aliasToCommand.put(alias, meta.name());
						if (!this.commandToAliases.containsKey(meta.name())) {
							this.commandToAliases.put(meta.name(), new ArrayList<String>());
						}
						this.commandToAliases.get(meta.name()).add(alias);
					}
				}
			}

			for (final Map.Entry<String, DispatchCommand> entry : this.dispatchers.entrySet()) {
				this.map.put(entry.getKey(), entry.getValue().getClass());
			}
		}
		return this.map;
	}

	@Override
	public void start(Environment env) throws IOException {
		try {
			parseCommandLine();
			if (Strings.isNullOrEmpty(this.commandName)) {
				final StringWriter msg = new StringWriter();
				msg.write(usage());
				throw new UnloggedFailure(1, msg.toString());
			}

			final BaseCommand cmd = getCommand();
			if (getName().isEmpty()) {
				cmd.setName(this.commandName);
			} else {
				cmd.setName(getName() + " " + this.commandName);
			}
			cmd.setArguments(this.args.toArray(new String[this.args.size()]));

			provideStateTo(cmd);
			// atomicCmd.set(cmd);
			cmd.start(env);

		}
		catch (final UnloggedFailure e) {
			String msg = e.getMessage();
			if (!msg.endsWith("\n")) {
				msg += "\n";
			}
			this.err.write(msg.getBytes(Charsets.UTF_8));
			this.err.flush();
			this.exit.onExit(e.exitCode);
		}
	}

	private BaseCommand getCommand() throws UnloggedFailure {
		final Map<String, Class<? extends BaseCommand>> map = getMap();
		String name = this.commandName;
		if (this.aliasToCommand.containsKey(this.commandName)) {
			name = this.aliasToCommand.get(name);
		}
		if (this.dispatchers.containsKey(name)) {
			return this.dispatchers.get(name);
		}
		final Class<? extends BaseCommand> c = map.get(name);
		if (c == null) {
			final String msg = (getName().isEmpty() ? "Gitblit" : getName()) + ": "
					+ this.commandName + ": not found";
			throw new UnloggedFailure(1, msg);
		}

		for (final BaseCommand cmd : this.instantiated) {
			// use an already instantiated command
			if (cmd.getClass().equals(c)) {
				return cmd;
			}
		}

		BaseCommand cmd = null;
		try {
			cmd = c.newInstance();
			this.instantiated.add(cmd);
		}
		catch (final Exception e) {
			throw new UnloggedFailure(1, MessageFormat.format("Failed to instantiate {0} command",
					this.commandName));
		}
		return cmd;
	}

	private boolean hasVisibleCommands() {
		boolean visible = false;
		for (final Class<? extends BaseCommand> cmd : this.commands) {
			visible |= !cmd.getAnnotation(CommandMetaData.class).hidden();
			if (visible) {
				return true;
			}
		}
		for (final DispatchCommand cmd : this.dispatchers.values()) {
			visible |= cmd.hasVisibleCommands();
			if (visible) {
				return true;
			}
		}
		return false;
	}

	public String getDescription() {
		return getClass().getAnnotation(CommandMetaData.class).description();
	}

	@Override
	public String usage() {
		final Set<String> cmds = new TreeSet<String>();
		final Set<String> dcs = new TreeSet<String>();
		final Map<String, String> displayNames = Maps.newHashMap();
		int maxLength = -1;
		final Map<String, Class<? extends BaseCommand>> m = getMap();
		for (final String name : m.keySet()) {
			final Class<? extends BaseCommand> c = m.get(name);
			final CommandMetaData meta = c.getAnnotation(CommandMetaData.class);
			if (meta.hidden()) {
				continue;
			}

			String displayName = name + (meta.admin() ? "*" : "");
			if (this.commandToAliases.containsKey(meta.name())) {
				displayName = name + (meta.admin() ? "*" : "") + " ("
						+ Joiner.on(',').join(this.commandToAliases.get(meta.name())) + ")";
			}
			displayNames.put(name, displayName);

			maxLength = Math.max(maxLength, displayName.length());
			if (DispatchCommand.class.isAssignableFrom(c)) {
				final DispatchCommand d = this.dispatchers.get(name);
				if (d.hasVisibleCommands()) {
					dcs.add(name);
				}
			} else {
				cmds.add(name);
			}
		}
		final String format = "%-" + maxLength + "s   %s";

		final StringBuilder usage = new StringBuilder();
		if (!StringUtils.isEmpty(getName())) {
			final String title = getName().toUpperCase() + ": " + getDescription();
			final String b = com.gitblit.utils.StringUtils.leftPad("", title.length() + 2, '═');
			usage.append('\n');
			usage.append(b).append('\n');
			usage.append(' ').append(title).append('\n');
			usage.append(b).append('\n');
			usage.append('\n');
		}

		if (!cmds.isEmpty()) {
			usage.append("Available commands");
			if (!getName().isEmpty()) {
				usage.append(" of ");
				usage.append(getName());
			}
			usage.append(" are:\n");
			usage.append("\n");
			for (final String name : cmds) {
				final Class<? extends Command> c = m.get(name);
				final String displayName = displayNames.get(name);
				final CommandMetaData meta = c.getAnnotation(CommandMetaData.class);
				usage.append("   ");
				usage.append(String.format(format, displayName,
						Strings.nullToEmpty(meta.description())));
				usage.append("\n");
			}
			usage.append("\n");
		}

		if (!dcs.isEmpty()) {
			usage.append("Available command dispatchers");
			if (!getName().isEmpty()) {
				usage.append(" of ");
				usage.append(getName());
			}
			usage.append(" are:\n");
			usage.append("\n");
			for (final String name : dcs) {
				final Class<? extends BaseCommand> c = m.get(name);
				final String displayName = displayNames.get(name);
				final CommandMetaData meta = c.getAnnotation(CommandMetaData.class);
				usage.append("   ");
				usage.append(String.format(format, displayName,
						Strings.nullToEmpty(meta.description())));
				usage.append("\n");
			}
			usage.append("\n");
		}

		usage.append("See '");
		if (!StringUtils.isEmpty(getName())) {
			usage.append(getName());
			usage.append(' ');
		}
		usage.append("COMMAND --help' for more information.\n");
		usage.append("\n");
		return usage.toString();
	}
}
