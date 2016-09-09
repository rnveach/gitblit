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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SessionAware;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Keys;
import com.gitblit.manager.IGitblit;
import com.gitblit.transport.ssh.SshDaemonClient;
import com.gitblit.utils.WorkQueue;
import com.google.common.util.concurrent.Atomics;
import com.google.common.util.concurrent.ThreadFactoryBuilder;


public class SshCommandFactory implements CommandFactory {
	private static final Logger logger = LoggerFactory.getLogger(SshCommandFactory.class);

	private final WorkQueue workQueue;
	private final IGitblit gitblit;
	private final ScheduledExecutorService startExecutor;
	private final ExecutorService destroyExecutor;

	public SshCommandFactory(IGitblit gitblit, WorkQueue workQueue) {
		this.gitblit = gitblit;
		this.workQueue = workQueue;

		final int threads = gitblit.getSettings().getInteger(Keys.git.sshCommandStartThreads, 2);
		this.startExecutor = workQueue.createQueue(threads, "SshCommandStart");
		this.destroyExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
				.setNameFormat("SshCommandDestroy-%s").setDaemon(true).build());
	}

	public void stop() {
		this.destroyExecutor.shutdownNow();
	}

	public RootDispatcher createRootDispatcher(SshDaemonClient client, String commandLine) {
		return new RootDispatcher(this.gitblit, client, commandLine, this.workQueue);
	}

	@Override
	public Command createCommand(final String commandLine) {
		return new Trampoline(commandLine);
	}

	private class Trampoline implements Command, SessionAware {
		private final String[] argv;
		private ServerSession session;
		private InputStream in;
		private OutputStream out;
		private OutputStream err;
		private ExitCallback exit;
		private Environment env;
		private final String cmdLine;
		private DispatchCommand cmd;
		private final AtomicBoolean logged;
		private final AtomicReference<Future<?>> task;

		Trampoline(String line) {
			if (line.startsWith("git-")) {
				line = "git " + line;
			}
			this.cmdLine = line;
			this.argv = split(line);
			this.logged = new AtomicBoolean();
			this.task = Atomics.newReference();
		}

		@Override
		public void setSession(ServerSession session) {
			this.session = session;
		}

		@Override
		public void setInputStream(final InputStream in) {
			this.in = in;
		}

		@Override
		public void setOutputStream(final OutputStream out) {
			this.out = out;
		}

		@Override
		public void setErrorStream(final OutputStream err) {
			this.err = err;
		}

		@Override
		public void setExitCallback(final ExitCallback callback) {
			this.exit = callback;
		}

		@Override
		public void start(final Environment env) throws IOException {
			this.env = env;
			this.task.set(SshCommandFactory.this.startExecutor.submit(new Runnable() {
				@Override
				public void run() {
					try {
						onStart();
					}
					catch (final Exception e) {
						logger.warn("Cannot start command ", e);
					}
				}

				@Override
				public String toString() {
					return "start (user " + Trampoline.this.session.getUsername() + ")";
				}
			}));
		}

		private void onStart() throws IOException {
			synchronized (this) {
				SshDaemonClient client = this.session.getAttribute(SshDaemonClient.KEY);
				try {
					this.cmd = createRootDispatcher(client, this.cmdLine);
					this.cmd.setArguments(this.argv);
					this.cmd.setInputStream(this.in);
					this.cmd.setOutputStream(this.out);
					this.cmd.setErrorStream(this.err);
					this.cmd.setExitCallback(new ExitCallback() {
						@Override
						public void onExit(int rc, String exitMessage) {
							Trampoline.this.exit.onExit(translateExit(rc), exitMessage);
							log(rc);
						}

						@Override
						public void onExit(int rc) {
							Trampoline.this.exit.onExit(translateExit(rc));
							log(rc);
						}
					});
					this.cmd.start(this.env);
				}
				finally {
					client = null;
				}
			}
		}

		private int translateExit(final int rc) {
			switch (rc) {
			case BaseCommand.STATUS_NOT_ADMIN:
				return 1;

			case BaseCommand.STATUS_CANCEL:
				return 15 /* SIGKILL */;

			case BaseCommand.STATUS_NOT_FOUND:
				return 127 /* POSIX not found */;

			default:
				return rc;
			}
		}

		private void log(final int rc) {
			if (this.logged.compareAndSet(false, true)) {
				logger.info("onExecute: {} exits with: {}", this.cmd.getClass().getSimpleName(), rc);
			}
		}

		@Override
		public void destroy() {
			final Future<?> future = this.task.getAndSet(null);
			if (future != null) {
				future.cancel(true);
				SshCommandFactory.this.destroyExecutor.execute(new Runnable() {
					@Override
					public void run() {
						onDestroy();
					}
				});
			}
		}

		private void onDestroy() {
			synchronized (this) {
				if (this.cmd != null) {
					try {
						this.cmd.destroy();
					}
					finally {
						this.cmd = null;
					}
				}
			}
		}
	}

	/** Split a command line into a string array. */
	static public String[] split(String commandLine) {
		final List<String> list = new ArrayList<String>();
		boolean inquote = false;
		boolean inDblQuote = false;
		StringBuilder r = new StringBuilder();
		for (int ip = 0; ip < commandLine.length();) {
			final char b = commandLine.charAt(ip++);
			switch (b) {
			case '\t':
			case ' ':
				if (inquote || inDblQuote) {
					r.append(b);
				} else if (r.length() > 0) {
					list.add(r.toString());
					r = new StringBuilder();
				}
				continue;
			case '\"':
				if (inquote) {
					r.append(b);
				} else {
					inDblQuote = !inDblQuote;
				}
				continue;
			case '\'':
				if (inDblQuote) {
					r.append(b);
				} else {
					inquote = !inquote;
				}
				continue;
			case '\\':
				if (inquote || (ip == commandLine.length())) {
					r.append(b); // literal within a quote
				} else {
					r.append(commandLine.charAt(ip++));
				}
				continue;
			default:
				r.append(b);
				continue;
			}
		}
		if (r.length() > 0) {
			list.add(r.toString());
		}
		return list.toArray(new String[list.size()]);
	}
}
