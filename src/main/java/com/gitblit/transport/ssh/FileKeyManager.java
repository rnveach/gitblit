/*
 * Copyright 2014 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.gitblit.transport.ssh;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.Keys;
import com.gitblit.manager.IRuntimeManager;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import com.google.inject.Inject;

/**
 * Manages public keys on the filesystem.
 *
 * @author James Moger
 *
 */
public class FileKeyManager extends IPublicKeyManager {

	protected final IRuntimeManager runtimeManager;

	protected final Map<File, Long> lastModifieds;

	@Inject
	public FileKeyManager(IRuntimeManager runtimeManager) {
		this.runtimeManager = runtimeManager;
		this.lastModifieds = new ConcurrentHashMap<File, Long>();
	}

	@Override
	public String toString() {
		final File dir = this.runtimeManager.getFileOrFolder(Keys.git.sshKeysFolder,
				"${baseFolder}/ssh");
		return MessageFormat.format("{0} ({1})", getClass().getSimpleName(), dir);
	}

	@Override
	public FileKeyManager start() {
		this.log.info(toString());
		return this;
	}

	@Override
	public boolean isReady() {
		return true;
	}

	@Override
	public FileKeyManager stop() {
		return this;
	}

	@Override
	protected boolean isStale(String username) {
		final File keystore = getKeystore(username);
		if (!keystore.exists()) {
			// keystore may have been deleted
			return true;
		}

		if (this.lastModifieds.containsKey(keystore)) {
			// compare modification times
			final long lastModified = this.lastModifieds.get(keystore);
			return lastModified != keystore.lastModified();
		}

		// assume stale
		return true;
	}

	@Override
	protected List<SshKey> getKeysImpl(String username) {
		try {
			this.log.info("loading ssh keystore for {}", username);
			final File keystore = getKeystore(username);
			if (!keystore.exists()) {
				return null;
			}
			if (keystore.exists()) {
				final List<SshKey> list = new ArrayList<SshKey>();
				for (final String entry : Files.readLines(keystore, Charsets.ISO_8859_1)) {
					if (entry.trim().length() == 0) {
						// skip blanks
						continue;
					}
					if (entry.charAt(0) == '#') {
						// skip comments
						continue;
					}
					final String[] parts = entry.split(" ", 2);
					final AccessPermission perm = AccessPermission.fromCode(parts[0]);
					if (perm.equals(AccessPermission.NONE)) {
						// ssh-rsa DATA COMMENT
						final SshKey key = new SshKey(entry);
						list.add(key);
					} else if (perm.exceeds(AccessPermission.NONE)) {
						// PERMISSION ssh-rsa DATA COMMENT
						final SshKey key = new SshKey(parts[1]);
						key.setPermission(perm);
						list.add(key);
					}
				}

				if (list.isEmpty()) {
					return null;
				}

				this.lastModifieds.put(keystore, keystore.lastModified());
				return list;
			}
		}
		catch (final IOException e) {
			throw new RuntimeException("Cannot read ssh keys", e);
		}
		return null;
	}

	/**
	 * Adds a unique key to the keystore. This function determines uniqueness by
	 * disregarding the comment/description field during key comparisons.
	 */
	@Override
	public boolean addKey(String username, SshKey key) {
		try {
			boolean replaced = false;
			final List<String> lines = new ArrayList<String>();
			final File keystore = getKeystore(username);
			if (keystore.exists()) {
				for (final String entry : Files.readLines(keystore, Charsets.ISO_8859_1)) {
					final String line = entry.trim();
					if (line.length() == 0) {
						// keep blanks
						lines.add(entry);
						continue;
					}
					if (line.charAt(0) == '#') {
						// keep comments
						lines.add(entry);
						continue;
					}

					final SshKey oldKey = parseKey(line);
					if (key.equals(oldKey)) {
						// replace key
						lines.add(key.getPermission() + " " + key.getRawData());
						replaced = true;
					} else {
						// retain key
						lines.add(entry);
					}
				}
			}

			if (!replaced) {
				// new key, append
				lines.add(key.getPermission() + " " + key.getRawData());
			}

			// write keystore
			final String content = Joiner.on("\n").join(lines).trim().concat("\n");
			Files.write(content, keystore, Charsets.ISO_8859_1);

			this.lastModifieds.remove(keystore);
			this.keyCache.invalidate(username);
			return true;
		}
		catch (final IOException e) {
			throw new RuntimeException("Cannot add ssh key", e);
		}
	}

	/**
	 * Removes the specified key from the keystore.
	 */
	@Override
	public boolean removeKey(String username, SshKey key) {
		try {
			final File keystore = getKeystore(username);
			if (keystore.exists()) {
				final List<String> lines = new ArrayList<String>();
				for (final String entry : Files.readLines(keystore, Charsets.ISO_8859_1)) {
					final String line = entry.trim();
					if (line.length() == 0) {
						// keep blanks
						lines.add(entry);
						continue;
					}
					if (line.charAt(0) == '#') {
						// keep comments
						lines.add(entry);
						continue;
					}

					// only include keys that are NOT rmKey
					final SshKey oldKey = parseKey(line);
					if (!key.equals(oldKey)) {
						lines.add(entry);
					}
				}
				if (lines.isEmpty()) {
					keystore.delete();
				} else {
					// write keystore
					final String content = Joiner.on("\n").join(lines).trim().concat("\n");
					Files.write(content, keystore, Charsets.ISO_8859_1);
				}

				this.lastModifieds.remove(keystore);
				this.keyCache.invalidate(username);
				return true;
			}
		}
		catch (final IOException e) {
			throw new RuntimeException("Cannot remove ssh key", e);
		}
		return false;
	}

	@Override
	public boolean removeAllKeys(String username) {
		final File keystore = getKeystore(username);
		if (keystore.delete()) {
			this.lastModifieds.remove(keystore);
			this.keyCache.invalidate(username);
			return true;
		}
		return false;
	}

	protected File getKeystore(String username) {
		final File dir = this.runtimeManager.getFileOrFolder(Keys.git.sshKeysFolder,
				"${baseFolder}/ssh");
		dir.mkdirs();
		final File keys = new File(dir, username + ".keys");
		return keys;
	}

	protected SshKey parseKey(String line) {
		final String[] parts = line.split(" ", 2);
		final AccessPermission perm = AccessPermission.fromCode(parts[0]);
		if (perm.equals(AccessPermission.NONE)) {
			// ssh-rsa DATA COMMENT
			final SshKey key = new SshKey(line);
			return key;
		} else {
			// PERMISSION ssh-rsa DATA COMMENT
			final SshKey key = new SshKey(parts[1]);
			key.setPermission(perm);
			return key;
		}
	}
}
