package com.gitblit.servlet;

import static org.junit.Assert.assertEquals;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;

import org.junit.Test;

import com.gitblit.internal.AbstractServletTest;
import com.gitblit.internal.TestHttpServletRequest;
import com.gitblit.internal.TestHttpServletResponse;

public class AccessDeniedServletTest extends AbstractServletTest {
	@Override
	protected AccessDeniedServlet getTestClass() {
		return new AccessDeniedServlet();
	}

	@Override
	protected String getURI() {
		return null;
	}

	@Test
	public void testPost() throws Exception {
		final TestHttpServletRequest request = createPost();
		final TestHttpServletResponse response = createResponse();

		doServletService(request, response);

		assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
	}

	@Test
	public void testGet() throws Exception {
		final TestHttpServletRequest request = createGet();
		final TestHttpServletResponse response = createResponse();

		doServletService(request, response);

		assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
	}
}
