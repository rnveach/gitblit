package com.gitblit.internal;

import java.util.Map;
import java.util.Properties;

import com.gitblit.IStoredSettings;

public final class TestStoredSettings extends IStoredSettings {
	private static final Properties properties = new Properties();

	public TestStoredSettings() {
		super(TestStoredSettings.class);
	}

	@Override
	protected Properties read() {
		return properties;
	}

	@Override
	public boolean saveSettings() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean saveSettings(Map<String, String> updatedSettings) {
		// TODO Auto-generated method stub
		return false;
	}

	// ////////////////////////////////////////////////////////////////////////

	public static void addProperty(String name, String value) {
		properties.put(name, value);
	}

	public static void clearProperties() {
		properties.clear();
	}
}
