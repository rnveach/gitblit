package com.gitblit.servlet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

import com.gitblit.internal.AbstractServletTest;
import com.gitblit.internal.GitUtils;
import com.gitblit.internal.TestHttpServletRequest;
import com.gitblit.internal.TestHttpServletResponse;
import com.gitblit.internal.TestRepositoryManager;
import com.gitblit.internal.TestStoredSettings;

public class BranchGraphServletTest extends AbstractServletTest {
	private final String REPOSITORY_NAME = "repositoryName";

	@Override
	protected BranchGraphServlet getTestClass() {
		return new BranchGraphServlet(new TestStoredSettings(),
				new TestRepositoryManager());
	}

	@Override
	protected String getURI() {
		return null;
	}

	@Test
	public void test() throws Exception {
		final TestHttpServletRequest request = createGet();
		final TestHttpServletResponse response = createResponse();

		doServletService(request, response);

		assertEquals(HttpServletResponse.SC_NOT_MODIFIED, response.getStatus());
		assertEquals("", response.toString());
		assertEquals(0, getTestClass().getLastModified(request));
	}

	@Test
	public void testNonExistantRepository() throws Exception {
		final TestHttpServletRequest request = createGet();
		final TestHttpServletResponse response = createResponse();

		request.setParameter("r", "doesn't_exist");

		// TODO: writes a few NPEs to log, should fix and more friendly
		doServletService(request, response);

		assertEquals(HttpServletResponse.SC_NOT_MODIFIED, response.getStatus());
		assertEquals("", response.toString());
		assertEquals(0, getTestClass().getLastModified(request));
	}

	@Test
	public void testRepositoryNoObjectId() throws Exception {
		final TestHttpServletRequest request = createGet();
		final TestHttpServletResponse response = createResponse();

		TestRepositoryManager.addRepository(REPOSITORY_NAME);

		request.setParameter("r", REPOSITORY_NAME);

		doServletService(request, response);

		assertEquals(HttpServletResponse.SC_NOT_MODIFIED, response.getStatus());
		assertEquals("", response.toString());
		assertEquals(0, getTestClass().getLastModified(request));
	}

	@Test
	public void testRepositoryWithOneCommit() throws Exception {
		final TestHttpServletRequest request = createGet();
		final TestHttpServletResponse response = createResponse();

		final Repository repository = GitUtils.createNewRepository();
		GitUtils.addAnEmptyFileAndCommit(repository, "file");

		TestRepositoryManager.addRepository(REPOSITORY_NAME, repository);

		request.setParameter("r", REPOSITORY_NAME);

		doServletService(request, response);

		assertEquals(0, response.getStatus());
		assertNotEquals("", response.toString());
		assertNull(response.getHeader("Cache-Control"));
		assertNotEquals(0, getTestClass().getLastModified(request));
	}

	@Test
	public void testRepositoryWithMultipleCommits() throws Exception {
		final TestHttpServletRequest request = createGet();
		final TestHttpServletResponse response = createResponse();

		final Repository repository = GitUtils.createNewRepository();
		GitUtils.addAnEmptyFileAndCommit(repository, "file1");
		GitUtils.addAnEmptyFileAndCommit(repository, "file2");

		TestRepositoryManager.addRepository(REPOSITORY_NAME, repository);

		request.setParameter("r", REPOSITORY_NAME);

		doServletService(request, response);

		assertEquals(0, response.getStatus());
		assertNotEquals("", response.toString());
		assertEquals("public, max-age=60, must-revalidate",
				response.getHeader("Cache-Control"));
		// TODO: date header
		assertNotEquals(0, getTestClass().getLastModified(request));
	}

	// TODO: specify object id parameter
	// TODO: specify length parameter

	@Test
	public void testAsLink() {
		assertEquals("baseUrl/graph/?r=repository&h=objectId&l=999",
				BranchGraphServlet.asLink("baseUrl", "repository", "objectId",
						999));
		assertEquals("baseUrl/graph/?r=repository",
				BranchGraphServlet.asLink("baseUrl", "repository", null, 0));
		assertEquals("baseUrl/graph/?r=repository",
				BranchGraphServlet.asLink("baseUrl/", "repository", null, 0));
		// TODO: is this right?
		assertEquals("/graph/?r=repository",
				BranchGraphServlet.asLink("", "repository", null, 0));
	}
}
