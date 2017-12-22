package com.gitblit.internal;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import ro.fortsoft.pf4j.PluginState;
import ro.fortsoft.pf4j.PluginWrapper;
import ro.fortsoft.pf4j.Version;

import com.gitblit.manager.IManager;
import com.gitblit.manager.IPluginManager;
import com.gitblit.models.PluginRegistry.InstallState;
import com.gitblit.models.PluginRegistry.PluginRegistration;
import com.gitblit.models.PluginRegistry.PluginRelease;

public final class TestPluginManager implements IPluginManager {

	private static TestPluginManager instance = new TestPluginManager();

	private TestPluginManager() {
	}

	public static TestPluginManager getInstance() {
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
	public Version getSystemVersion() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void startPlugins() {
		// TODO Auto-generated method stub

	}

	@Override
	public void stopPlugins() {
		// TODO Auto-generated method stub

	}

	@Override
	public PluginState startPlugin(String pluginId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PluginState stopPlugin(String pluginId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Class<?>> getExtensionClasses(String pluginId) {
		return Collections.emptyList();
	}

	@Override
	public <T> List<T> getExtensions(Class<T> type) {
		return Collections.emptyList();
	}

	@Override
	public List<PluginWrapper> getPlugins() {
		return Collections.emptyList();
	}

	@Override
	public PluginWrapper getPlugin(String pluginId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PluginWrapper whichPlugin(Class<?> clazz) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean disablePlugin(String pluginId) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean enablePlugin(String pluginId) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean uninstallPlugin(String pluginId) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean refreshRegistry(boolean verifyChecksum) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean installPlugin(String url, boolean verifyChecksum)
			throws IOException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean upgradePlugin(String pluginId, String url,
			boolean verifyChecksum) throws IOException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public List<PluginRegistration> getRegisteredPlugins() {
		return Collections.emptyList();
	}

	@Override
	public List<PluginRegistration> getRegisteredPlugins(InstallState state) {
		return Collections.emptyList();
	}

	@Override
	public PluginRegistration lookupPlugin(String idOrName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PluginRelease lookupRelease(String idOrName, String version) {
		// TODO Auto-generated method stub
		return null;
	}
}
