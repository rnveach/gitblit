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
package com.gitblit;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.RepositoryManager;
import com.gitblit.manager.RuntimeManager;
import com.gitblit.tickets.BranchTicketService;
import com.gitblit.tickets.FileTicketService;
import com.gitblit.tickets.ITicketService;
import com.gitblit.tickets.RedisTicketService;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.XssFilter;
import com.gitblit.utils.XssFilter.AllowXssFilter;

/**
 * A command-line tool to reindex all tickets in all repositories when the
 * indexes needs to be rebuilt.
 *
 * @author James Moger
 *
 */
public class ReindexTickets {

	public static void main(String... args) {
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
				ReindexTickets.usage(parser, null);
				return;
			}
		}
		catch (final CmdLineException t) {
			ReindexTickets.usage(parser, t);
			return;
		}

		// load the settings
		FileSettings settings = params.FILESETTINGS;
		if (!StringUtils.isEmpty(params.settingsfile)) {
			if (new File(params.settingsfile).exists()) {
				settings = new FileSettings(params.settingsfile);
			}
		}

		// reindex tickets
		reindex(new File(Params.baseFolder), settings);
		throw new RuntimeException("System.exit(0);");
	}

	/**
	 * Display the command line usage of ReindexTickets.
	 *
	 * @param parser
	 * @param t
	 */
	private static final void usage(CmdLineParser parser, CmdLineException t) {
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
					"\nExample:\n  java -gitblit.jar com.gitblit.ReindexTickets --baseFolder c:\\gitblit-data");
		}
		throw new RuntimeException("System.exit(0);");
	}

	/**
	 * Reindex all tickets
	 *
	 * @param settings
	 */
	private static void reindex(File baseFolder, IStoredSettings settings) {
		// disable some services
		settings.overrideSetting(Keys.web.allowLuceneIndexing, false);
		settings.overrideSetting(Keys.git.enableGarbageCollection, false);
		settings.overrideSetting(Keys.git.enableMirroring, false);
		settings.overrideSetting(Keys.web.activityCacheDays, 0);

		final XssFilter xssFilter = new AllowXssFilter();
		final IRuntimeManager runtimeManager = new RuntimeManager(settings, xssFilter, baseFolder)
				.start();
		final IRepositoryManager repositoryManager = new RepositoryManager(runtimeManager, null,
				null).start();

		final String serviceName = settings.getString(Keys.tickets.service,
				BranchTicketService.class.getSimpleName());
		if (StringUtils.isEmpty(serviceName)) {
			System.err.println(MessageFormat.format("Please define a ticket service in \"{0}\"",
					Keys.tickets.service));
			throw new RuntimeException("System.exit(1);");
		}
		ITicketService ticketService = null;
		try {
			final Class<?> serviceClass = Class.forName(serviceName);
			if (RedisTicketService.class.isAssignableFrom(serviceClass)) {
				// Redis ticket service
				ticketService = new RedisTicketService(runtimeManager, null, null, null,
						repositoryManager).start();
			} else if (BranchTicketService.class.isAssignableFrom(serviceClass)) {
				// Branch ticket service
				ticketService = new BranchTicketService(runtimeManager, null, null, null,
						repositoryManager).start();
			} else if (FileTicketService.class.isAssignableFrom(serviceClass)) {
				// File ticket service
				ticketService = new FileTicketService(runtimeManager, null, null, null,
						repositoryManager).start();
			} else {
				System.err.println("Unknown ticket service " + serviceName);
				throw new RuntimeException("System.exit(1);");
			}
		}
		catch (final Exception e) {
			e.printStackTrace();
			throw new RuntimeException("System.exit(1);");
		}

		ticketService.reindex();
		ticketService.stop();
		repositoryManager.stop();
		runtimeManager.stop();
	}

	/**
	 * Parameters.
	 */
	public static class Params {

		public static String baseFolder;

		@Option(name = "--help", aliases = { "-h" }, usage = "Show this help")
		public Boolean help = false;

		private final FileSettings FILESETTINGS = new FileSettings(
				new File(baseFolder, Constants.PROPERTIES_FILE).getAbsolutePath());

		@Option(name = "--repositoriesFolder", usage = "Git Repositories Folder", metaVar = "PATH")
		public String repositoriesFolder = this.FILESETTINGS.getString(Keys.git.repositoriesFolder,
				"git");

		@Option(name = "--settings", usage = "Path to alternative settings", metaVar = "FILE")
		public String settingsfile;
	}
}
