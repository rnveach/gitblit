package com.gitblit.internal;

import java.io.IOException;

import org.junit.After;

public abstract class AbstractTest {
	@After
	public final void cleanUp() throws IOException {
		GitUtils.clearTempRepositories();
		TestStoredSettings.clearProperties();
	}
}
