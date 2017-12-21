package com.gitblit.internal;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

public final class TestHttpServletResponse implements HttpServletResponse {
	private final Map<String, List<String>> headers = new HashMap<String, List<String>>();
	private String contentType;
	private int status;
	private final StringBuffer content = new StringBuffer();

	@Override
	public String getCharacterEncoding() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getContentType() {
		return this.contentType;
	}

	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		return new ServletOutputStream() {
			public void write(int arg0) throws IOException {
				TestHttpServletResponse.this.content.append((char) arg0);
			}

			@Override
			public boolean isReady() {
				return true;
			}

			@Override
			public void setWriteListener(WriteListener writeListener) {
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override
	public PrintWriter getWriter() throws IOException {
		return new PrintWriter(new Writer() {
			/** {@inheritDoc} */
			@Override
			public void close() throws IOException {
			}

			/** {@inheritDoc} */
			@Override
			public void flush() throws IOException {
			}

			/** {@inheritDoc} */
			@Override
			public void write(char[] cbuf, int off, int len) throws IOException {
				TestHttpServletResponse.this.content.append(cbuf, off, len);
			}
		});
	}

	@Override
	public void setCharacterEncoding(String charset) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setContentLength(int len) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setContentLengthLong(long len) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setContentType(String type) {
		this.contentType = type;
	}

	@Override
	public void setBufferSize(int size) {
		// TODO Auto-generated method stub

	}

	@Override
	public int getBufferSize() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void flushBuffer() throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public void resetBuffer() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isCommitted() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void reset() {
		// TODO Auto-generated method stub

	}

	@Override
	public void setLocale(Locale loc) {
		// TODO Auto-generated method stub

	}

	@Override
	public Locale getLocale() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addCookie(Cookie cookie) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean containsHeader(String name) {
		return this.headers.containsKey(name);
	}

	@Override
	public String encodeURL(String url) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String encodeRedirectURL(String url) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String encodeUrl(String url) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String encodeRedirectUrl(String url) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void sendError(int sc, String msg) throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public void sendError(int sc) throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public void sendRedirect(String location) throws IOException {
		setHeader("Location", location);
		setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
	}

	@Override
	public void setDateHeader(String name, long date) {
		// TODO Auto-generated method stub

	}

	@Override
	public void addDateHeader(String name, long date) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setHeader(String name, String value) {
		this.headers.put(name, Arrays.asList(value));
	}

	@Override
	public void addHeader(String name, String value) {
		List<String> holder = this.headers.get(name);
		if (holder == null)
			holder = new ArrayList<String>();

		holder.add(value);
		this.headers.put(name, holder);
	}

	@Override
	public void setIntHeader(String name, int value) {
		setHeader(name, Integer.toString(value));
	}

	@Override
	public void addIntHeader(String name, int value) {
		addHeader(name, Integer.toString(value));
	}

	@Override
	public void setStatus(int sc) {
		this.status = sc;
	}

	@Override
	public void setStatus(int sc, String sm) {
		// TODO Auto-generated method stub
		this.status = sc;
	}

	@Override
	public int getStatus() {
		return this.status;
	}

	@Override
	public String getHeader(String name) {
		final List<String> list = this.headers.get(name);

		if ((list == null) || (list.size() == 0))
			return null;

		return list.get(0);
	}

	@Override
	public Collection<String> getHeaders(String name) {
		return Collections.unmodifiableList(this.headers.get(name));
	}

	@Override
	public Collection<String> getHeaderNames() {
		return Collections.unmodifiableCollection(this.headers.keySet());
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return this.content.toString();
	}
}
