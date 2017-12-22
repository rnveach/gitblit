package com.gitblit.internal;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;

public abstract class AbstractTest {
	@Before
	public final void initialize() throws IOException {
		TestStoredSettings.reset();
		TestAuthenticationManager.reset();
	}

	@After
	public final void cleanUp() throws IOException {
		GitUtils.clearTempRepositories();
	}
}
