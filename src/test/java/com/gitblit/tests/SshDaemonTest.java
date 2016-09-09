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
package com.gitblit.tests;

import java.io.File;
import java.text.MessageFormat;
import java.util.List;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Test;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.JGitUtils;

public class SshDaemonTest extends SshUnitTest {

	static File ticgitFolder = new File(GitBlitSuite.REPOSITORIES, "working/ticgit");

	String url = GitBlitSuite.sshDaemonUrl;

	@Test
	public void testPublicKeyAuthentication() throws Exception {
		final SshClient client = getClient();
		final ClientSession session = client
				.connect(this.username, "localhost", GitBlitSuite.sshPort).await().getSession();
		session.addPublicKeyIdentity(this.rwKeyPair);
		assertTrue(session.auth().await().isSuccess());
	}

	@Test
	public void testVersionCommand() throws Exception {
		final String result = testSshCommand("version");
		assertEquals(Constants.getGitBlitVersion(), result);
	}

	@Test
	public void testCloneCommand() throws Exception {
		if (ticgitFolder.exists()) {
			GitBlitSuite.close(ticgitFolder);
			FileUtils.delete(ticgitFolder, FileUtils.RECURSIVE);
		}

		// set clone restriction
		final RepositoryModel model = repositories().getRepositoryModel("ticgit.git");
		model.accessRestriction = AccessRestrictionType.CLONE;
		model.authorizationControl = AuthorizationControl.NAMED;
		repositories().updateRepositoryModel(model.name, model, false);

		final JschConfigTestSessionFactory sessionFactory = new JschConfigTestSessionFactory(
				this.roKeyPair);
		SshSessionFactory.setInstance(sessionFactory);

		final CloneCommand clone = Git.cloneRepository();
		clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider(this.username,
				this.password));
		clone.setURI(MessageFormat.format("{0}/ticgit.git", this.url));
		clone.setDirectory(ticgitFolder);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		final Git git = clone.call();
		final List<RevCommit> commits = JGitUtils.getRevLog(git.getRepository(), 10);
		GitBlitSuite.close(git);
		assertEquals(10, commits.size());

		// restore anonymous repository access
		model.accessRestriction = AccessRestrictionType.NONE;
		model.authorizationControl = AuthorizationControl.NAMED;
		repositories().updateRepositoryModel(model.name, model, false);
	}
}
