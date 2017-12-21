package com.gitblit.internal;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class AbstractServletTest extends AbstractTest {
	protected abstract HttpServlet getTestClass();

	protected abstract String getURI();

	protected TestHttpServletRequest createPost() {
		return new TestHttpServletRequest("POST", getURI());
	}

	protected TestHttpServletRequest createGet() {
		return new TestHttpServletRequest("GET", getURI());
	}

	protected TestHttpServletResponse createResponse() {
		return new TestHttpServletResponse();
	}

	protected void doServletService(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		getTestClass().service(request, response);
	}
}
