package com.gitblit.internal;

import java.io.File;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import com.gitblit.IStoredSettings;
import com.gitblit.guice.CoreModule;
import com.gitblit.guice.WebModule;
import com.gitblit.manager.IManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.ServerStatus;
import com.gitblit.utils.XssFilter;
import com.google.inject.Guice;
import com.google.inject.Injector;

public final class TestRuntimeManager implements IRuntimeManager {
	private static final Injector injector = Guice.createInjector(
			new CoreModule(), new WebModule());

	private static TestRuntimeManager instance = new TestRuntimeManager();

	private TestRuntimeManager() {
	}

	public static TestRuntimeManager getInstance() {
		return instance;
	}

	@Override
	public IManager start() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IManager stop() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Injector getInjector() {
		return injector;
	}

	@Override
	public void setBaseFolder(File folder) {
		// TODO Auto-generated method stub

	}

	@Override
	public File getBaseFolder() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public TimeZone getTimezone() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Locale getLocale() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isDebugMode() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Date getBootDate() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ServerStatus getStatus() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ServerSettings getSettingsModel() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public File getFileOrFolder(String key, String defaultFileOrFolder) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public File getFileOrFolder(String fileOrFolder) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IStoredSettings getSettings() {
		return TestStoredSettings.getInstance();
	}

	@Override
	public boolean updateSettings(Map<String, String> updatedSettings) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public XssFilter getXssFilter() {
		// TODO Auto-generated method stub
		return null;
	}
}
