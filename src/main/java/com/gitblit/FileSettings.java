/*
 * Copyright 2011 gitblit.com.
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
package com.gitblit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.gitblit.utils.FileUtils;
import com.gitblit.utils.StringUtils;

/**
 * Dynamically loads and reloads a properties file by keeping track of the last
 * modification date.
 *
 * @author James Moger
 *
 */
public class FileSettings extends IStoredSettings {

	private File propertiesFile;

	private final Properties properties = new Properties();

	private volatile long lastModified;

	private volatile boolean forceReload;

	public FileSettings() {
		super(FileSettings.class);
	}

	public FileSettings(String file) {
		this();
		load(file);
	}

	public void load(String file) {
		this.propertiesFile = new File(file);
	}

	/**
	 * Merges the provided settings into this instance. This will also set the
	 * target file for this instance IFF it is unset AND the merge source is
	 * also a FileSettings. This is a little sneaky.
	 */
	@Override
	public void merge(IStoredSettings settings) {
		super.merge(settings);

		// sneaky: set the target file from the merge source
		if ((this.propertiesFile == null) && (settings instanceof FileSettings)) {
			this.propertiesFile = ((FileSettings) settings).propertiesFile;
		}
	}

	/**
	 * Returns a properties object which contains the most recent contents of
	 * the properties file.
	 */
	@Override
	protected synchronized Properties read() {
		if ((this.propertiesFile != null) && this.propertiesFile.exists()
				&& (this.forceReload || (this.propertiesFile.lastModified() > this.lastModified))) {
			FileInputStream is = null;
			try {
				this.logger.debug("loading {}", this.propertiesFile);
				Properties props = new Properties();
				is = new FileInputStream(this.propertiesFile);
				props.load(is);

				// ticket-110
				props = readIncludes(props);

				// load properties after we have successfully read file
				this.properties.clear();
				this.properties.putAll(props);
				this.lastModified = this.propertiesFile.lastModified();
				this.forceReload = false;
			}
			catch (final FileNotFoundException f) {
				// IGNORE - won't happen because file.exists() check above
			}
			catch (final Throwable t) {
				this.logger.error("Failed to read " + this.propertiesFile.getName(), t);
			}
			finally {
				if (is != null) {
					try {
						is.close();
					}
					catch (final Throwable t) {
						// IGNORE
					}
				}
			}
		}
		return this.properties;
	}

	/**
	 * Recursively read "include" properties files.
	 *
	 * @param properties
	 * @return
	 * @throws IOException
	 */
	private Properties readIncludes(Properties properties) throws IOException {

		Properties baseProperties = new Properties();

		final String include = (String) properties.remove("include");
		if (!StringUtils.isEmpty(include)) {

			// allow for multiples
			final List<String> names = StringUtils.getStringsFromValue(include, ",");
			for (final String name : names) {

				if (StringUtils.isEmpty(name)) {
					continue;
				}

				// try co-located
				File file = new File(this.propertiesFile.getParentFile(), name.trim());
				if (!file.exists()) {
					// try absolute path
					file = new File(name.trim());
				}

				if (!file.exists()) {
					this.logger.warn("failed to locate {}", file);
					continue;
				}

				// load properties
				this.logger.debug("loading {}", file);
				try (FileInputStream iis = new FileInputStream(file)) {
					baseProperties.load(iis);
				}

				// read nested includes
				baseProperties = readIncludes(baseProperties);

			}

		}

		// includes are "default" properties, they must be set first and the
		// props which specified the "includes" must override
		final Properties merged = new Properties();
		merged.putAll(baseProperties);
		merged.putAll(properties);

		return merged;
	}

	@Override
	public boolean saveSettings() {
		String content = FileUtils.readContent(this.propertiesFile, "\n");
		for (final String key : this.removals) {
			final String regex = "(?m)^(" + regExEscape(key) + "\\s*+=\\s*+)"
					+ "(?:[^\r\n\\\\]++|\\\\(?:\r?\n|\r|.))*+$";
			content = content.replaceAll(regex, "");
		}
		this.removals.clear();

		FileUtils.writeContent(this.propertiesFile, content);
		// manually set the forceReload flag because not all JVMs support real
		// millisecond resolution of lastModified. (issue-55)
		this.forceReload = true;
		return true;
	}

	/**
	 * Updates the specified settings in the settings file.
	 */
	@Override
	public synchronized boolean saveSettings(Map<String, String> settings) {
		String content = FileUtils.readContent(this.propertiesFile, "\n");
		for (final Map.Entry<String, String> setting : settings.entrySet()) {
			final String regex = "(?m)^(" + regExEscape(setting.getKey()) + "\\s*+=\\s*+)"
					+ "(?:[^\r\n\\\\]++|\\\\(?:\r?\n|\r|.))*+$";
			final String oldContent = content;
			content = content.replaceAll(regex, setting.getKey() + " = " + setting.getValue());
			if (content.equals(oldContent)) {
				// did not replace value because it does not exist in the file
				// append new setting to content (issue-85)
				content += "\n" + setting.getKey() + " = " + setting.getValue();
			}
		}
		FileUtils.writeContent(this.propertiesFile, content);
		// manually set the forceReload flag because not all JVMs support real
		// millisecond resolution of lastModified. (issue-55)
		this.forceReload = true;
		return true;
	}

	private static String regExEscape(String input) {
		return input.replace(".", "\\.").replace("$", "\\$").replace("{", "\\{");
	}

	@Override
	public String toString() {
		return this.propertiesFile.getAbsolutePath();
	}
}
