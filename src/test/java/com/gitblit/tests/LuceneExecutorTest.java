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
package com.gitblit.tests;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.gitblit.Keys;
import com.gitblit.manager.RepositoryManager;
import com.gitblit.manager.RuntimeManager;
import com.gitblit.manager.UserManager;
import com.gitblit.models.RefModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.SearchResult;
import com.gitblit.service.LuceneService;
import com.gitblit.tests.mock.MemorySettings;
import com.gitblit.utils.FileUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.XssFilter;
import com.gitblit.utils.XssFilter.AllowXssFilter;


/**
 * Tests Lucene indexing and querying.
 *
 * @author James Moger
 *
 */
public class LuceneExecutorTest extends GitblitUnitTest {

	LuceneService lucene;

	private static LuceneService newLuceneExecutor() {
		final MemorySettings settings = new MemorySettings();
		settings.put(Keys.git.repositoriesFolder, GitBlitSuite.REPOSITORIES);
		final XssFilter xssFilter = new AllowXssFilter();
		final RuntimeManager runtime = new RuntimeManager(settings, xssFilter,
				GitBlitSuite.BASEFOLDER).start();
		final UserManager users = new UserManager(runtime, null).start();
		final RepositoryManager repos = new RepositoryManager(runtime, null, users);
		return new LuceneService(settings, repos);
	}

	private static RepositoryModel newRepositoryModel(Repository repository) {
		final RepositoryModel model = new RepositoryModel();
		model.name = FileUtils
				.getRelativePath(GitBlitSuite.REPOSITORIES, repository.getDirectory());
		model.hasCommits = JGitUtils.hasCommits(repository);

		// index all local branches
		model.indexedBranches = new ArrayList<String>();
		for (final RefModel ref : JGitUtils.getLocalBranches(repository, true, -1)) {
			model.indexedBranches.add(ref.getName());
		}
		return model;
	}

	@Before
	public void setup() {
		this.lucene = newLuceneExecutor();
	}

	@After
	public void tearDown() {
		this.lucene.close();
	}

	@Test
	public void testIndex() {
		// reindex helloworld
		Repository repository = GitBlitSuite.getHelloworldRepository();
		RepositoryModel model = newRepositoryModel(repository);
		this.lucene.reindex(model, repository);
		repository.close();

		SearchResult result = this.lucene.search("type:blob AND path:bit.bit", 1, 1, model.name)
				.get(0);
		assertEquals("Mike Donaghy", result.author);
		result = this.lucene.search("type:blob AND path:clipper.prg", 1, 1, model.name).get(0);
		assertEquals("tinogomes", result.author);

		// reindex JGit
		repository = GitBlitSuite.getJGitRepository();
		model = newRepositoryModel(repository);
		this.lucene.reindex(model, repository);
		repository.close();
	}

	@Test
	public void testQuery() {
		// 2 occurrences on the master branch
		Repository repository = GitBlitSuite.getHelloworldRepository();
		RepositoryModel model = newRepositoryModel(repository);
		repository.close();

		List<SearchResult> results = this.lucene.search("ada", 1, 10, model.name);
		assertEquals(2, results.size());
		for (final SearchResult res : results) {
			assertEquals("refs/heads/master", res.branch);
		}

		// author test
		results = this.lucene.search("author: tinogomes AND type:commit", 1, 10, model.name);
		assertEquals(2, results.size());

		// blob test
		results = this.lucene.search("type: blob AND \"import std.stdio\"", 1, 10, model.name);
		assertEquals(1, results.size());
		assertEquals("d.D", results.get(0).path);

		// commit test
		repository = GitBlitSuite.getJGitRepository();
		model = newRepositoryModel(repository);
		repository.close();

		results = this.lucene.search("\"initial jgit contribution to eclipse.org\"", 1, 10,
				model.name);
		assertEquals(1, results.size());
		assertEquals("Git Development Community", results.get(0).author);
		assertEquals("1a6964c8274c50f0253db75f010d78ef0e739343", results.get(0).commitId);
		assertEquals("refs/heads/master", results.get(0).branch);

		// hash id tests
		results = this.lucene.search(
				"type:commit AND commit:1a6964c8274c50f0253db75f010d78ef0e739343", 1, 10,
				model.name);
		assertEquals(1, results.size());

		results = this.lucene.search("type:commit AND commit:1a6964c8274*", 1, 10, model.name);
		assertEquals("Shawn O. Pearce", results.get(0).committer);
		assertEquals(1, results.size());

		// annotated tag test
		results = this.lucene
				.search("I663208919f297836a9c16bf458e4a43ffaca4c12", 1, 10, model.name);
		assertEquals(1, results.size());
		assertEquals("[v1.3.0.201202151440-r]", results.get(0).tags.toString());
	}

	@Test
	public void testMultiSearch() {
		final List<String> list = new ArrayList<String>();
		Repository repository = GitBlitSuite.getHelloworldRepository();
		list.add(newRepositoryModel(repository).name);
		repository.close();

		repository = GitBlitSuite.getJGitRepository();
		list.add(newRepositoryModel(repository).name);
		repository.close();

		final List<SearchResult> results = this.lucene.search("test", 1, 10, list);
		assertEquals(10, results.size());
	}

	@Test
	public void testDeleteBlobFromIndex() throws Exception {
		// start with a fresh reindex of entire repository
		final Repository repository = GitBlitSuite.getHelloworldRepository();
		final RepositoryModel model = newRepositoryModel(repository);
		this.lucene.reindex(model, repository);

		// now delete a blob
		assertTrue(this.lucene.deleteBlob(model.name, "refs/heads/master", "java.java"));
		assertFalse(this.lucene.deleteBlob(model.name, "refs/heads/master", "java.java"));
	}
}