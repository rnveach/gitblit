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
package com.gitblit.transport.ssh.git;

import java.io.IOException;

import org.apache.sshd.server.Environment;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;
import org.kohsuke.args4j.Argument;

import com.gitblit.git.GitblitReceivePackFactory;
import com.gitblit.git.GitblitUploadPackFactory;
import com.gitblit.git.RepositoryResolver;
import com.gitblit.transport.ssh.SshDaemonClient;
import com.gitblit.transport.ssh.commands.BaseCommand;

abstract class BaseGitCommand extends BaseCommand {
	@Argument(index = 0, metaVar = "REPOSITORY", required = true, usage = "repository name")
	protected String repository;

	protected RepositoryResolver<SshDaemonClient> repositoryResolver;
	protected ReceivePackFactory<SshDaemonClient> receivePackFactory;
	protected UploadPackFactory<SshDaemonClient> uploadPackFactory;

	protected Repository repo;

	@Override
	public void destroy() {
		super.destroy();

		this.repositoryResolver = null;
		this.receivePackFactory = null;
		this.uploadPackFactory = null;
		this.repo = null;
	}

	@Override
	public void start(final Environment env) {
		startThread(new RepositoryCommandRunnable() {
			@Override
			public void run() throws Exception {
				parseCommandLine();
				BaseGitCommand.this.service();
			}

			@Override
			public String getRepository() {
				return BaseGitCommand.this.repository;
			}
		});
	}

	private void service() throws IOException, Failure {
		try {
			this.repo = openRepository();
			runImpl();
		}
		finally {
			if (this.repo != null) {
				this.repo.close();
			}
		}
	}

	protected abstract void runImpl() throws IOException, Failure;

	protected Repository openRepository() throws Failure {
		// Assume any attempt to use \ was by a Windows client
		// and correct to the more typical / used in Git URIs.
		//
		this.repository = this.repository.replace('\\', '/');
		// ssh://git@thishost/path should always be name="/path" here
		//
		if (this.repository.startsWith("/")) {
			this.repository = this.repository.substring(1);
		}
		try {
			return this.repositoryResolver.open(getContext().getClient(), this.repository);
		}
		catch (final Exception e) {
			throw new Failure(1, "fatal: '" + this.repository + "': not a git archive", e);
		}
	}

	public void setRepositoryResolver(RepositoryResolver<SshDaemonClient> repositoryResolver) {
		this.repositoryResolver = repositoryResolver;
	}

	public void setReceivePackFactory(GitblitReceivePackFactory<SshDaemonClient> receivePackFactory) {
		this.receivePackFactory = receivePackFactory;
	}

	public void setUploadPackFactory(GitblitUploadPackFactory<SshDaemonClient> uploadPackFactory) {
		this.uploadPackFactory = uploadPackFactory;
	}
}