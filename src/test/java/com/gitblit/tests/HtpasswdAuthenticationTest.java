/*
 * Copyright 2013 gitblit.com.
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
package com.gitblit.tests;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.gitblit.IStoredSettings;
import com.gitblit.auth.HtpasswdAuthProvider;
import com.gitblit.manager.AuthenticationManager;
import com.gitblit.manager.RuntimeManager;
import com.gitblit.manager.UserManager;
import com.gitblit.models.UserModel;
import com.gitblit.tests.mock.MemorySettings;
import com.gitblit.utils.XssFilter;
import com.gitblit.utils.XssFilter.AllowXssFilter;

/**
 * Test the Htpasswd user service.
 *
 */
public class HtpasswdAuthenticationTest extends GitblitUnitTest {

	private static final String RESOURCE_DIR = "src/test/resources/htpasswd/";
	private static final String KEY_SUPPORT_PLAINTEXT_PWD = "realm.htpasswd.supportPlaintextPasswords";

	private static final int NUM_USERS_HTPASSWD = 10;

	private static final MemorySettings MS = new MemorySettings(new HashMap<String, Object>());

	private HtpasswdAuthProvider htpasswd;

	private AuthenticationManager auth;

	private static MemorySettings getSettings(String userfile, String groupfile, Boolean overrideLA) {
		MS.put("realm.userService", RESOURCE_DIR + "users.conf");
		MS.put("realm.htpasswd.userfile", (userfile == null) ? (RESOURCE_DIR + "htpasswd")
				: userfile);
		MS.put("realm.htpasswd.groupfile", (groupfile == null) ? (RESOURCE_DIR + "htgroup")
				: groupfile);
		MS.put("realm.htpasswd.overrideLocalAuthentication", (overrideLA == null) ? "false"
				: overrideLA.toString());
		// Default to keep test the same on all platforms.
		MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "false");

		return MS;
	}

	private static MemorySettings getSettings() {
		return getSettings(null, null, null);
	}

	private void setupUS() {
		this.htpasswd = newHtpasswdAuthentication(getSettings());
		this.auth = newAuthenticationManager(getSettings());
	}

	private static HtpasswdAuthProvider newHtpasswdAuthentication(IStoredSettings settings) {
		final XssFilter xssFilter = new AllowXssFilter();
		final RuntimeManager runtime = new RuntimeManager(settings, xssFilter,
				GitBlitSuite.BASEFOLDER).start();
		final UserManager users = new UserManager(runtime, null).start();
		final HtpasswdAuthProvider htpasswd = new HtpasswdAuthProvider();
		htpasswd.setup(runtime, users);
		return htpasswd;
	}

	private static AuthenticationManager newAuthenticationManager(IStoredSettings settings) {
		final XssFilter xssFilter = new AllowXssFilter();
		final RuntimeManager runtime = new RuntimeManager(settings, xssFilter,
				GitBlitSuite.BASEFOLDER).start();
		final UserManager users = new UserManager(runtime, null).start();
		final HtpasswdAuthProvider htpasswd = new HtpasswdAuthProvider();
		htpasswd.setup(runtime, users);
		final AuthenticationManager auth = new AuthenticationManager(runtime, users);
		auth.addAuthenticationProvider(htpasswd);
		return auth;
	}

	private static void copyInFiles() throws IOException {
		final File dir = new File(RESOURCE_DIR);
		final FilenameFilter filter = new FilenameFilter() {
			@Override
			public boolean accept(File dir, String file) {
				return file.endsWith(".in");
			}
		};
		for (final File inf : dir.listFiles(filter)) {
			final File dest = new File(inf.getParent(), inf.getName().substring(0,
					inf.getName().length() - 3));
			FileUtils.copyFile(inf, dest);
		}
	}

	private static void deleteGeneratedFiles() {
		final File dir = new File(RESOURCE_DIR);
		final FilenameFilter filter = new FilenameFilter() {
			@Override
			public boolean accept(File dir, String file) {
				return !(file.endsWith(".in"));
			}
		};
		for (final File file : dir.listFiles(filter)) {
			file.delete();
		}
	}

	@Before
	public void setup() throws IOException {
		copyInFiles();
		setupUS();
	}

	@After
	public void tearDown() {
		deleteGeneratedFiles();
	}

	@Test
	public void testSetup() {
		assertEquals(NUM_USERS_HTPASSWD, this.htpasswd.getNumberHtpasswdUsers());
	}

	@Test
	public void testAuthenticate() {
		MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "true");
		UserModel user = this.htpasswd.authenticate("user1", "pass1".toCharArray());
		assertNotNull(user);
		assertEquals("user1", user.username);

		user = this.htpasswd.authenticate("user2", "pass2".toCharArray());
		assertNotNull(user);
		assertEquals("user2", user.username);

		// Test different encryptions
		user = this.htpasswd.authenticate("plain", "passWord".toCharArray());
		assertNotNull(user);
		assertEquals("plain", user.username);

		MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "false");
		user = this.htpasswd.authenticate("crypt", "password".toCharArray());
		assertNotNull(user);
		assertEquals("crypt", user.username);

		user = this.htpasswd.authenticate("md5", "password".toCharArray());
		assertNotNull(user);
		assertEquals("md5", user.username);

		user = this.htpasswd.authenticate("sha", "password".toCharArray());
		assertNotNull(user);
		assertEquals("sha", user.username);

		// Test leading and trailing whitespace
		user = this.htpasswd.authenticate("trailing", "whitespace".toCharArray());
		assertNotNull(user);
		assertEquals("trailing", user.username);

		user = this.htpasswd.authenticate("tabbed", "frontAndBack".toCharArray());
		assertNotNull(user);
		assertEquals("tabbed", user.username);

		user = this.htpasswd.authenticate("leading", "whitespace".toCharArray());
		assertNotNull(user);
		assertEquals("leading", user.username);
	}

	@Test
	public void testAuthenticationManager() {
		MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "true");
		UserModel user = this.auth.authenticate("user1", "pass1".toCharArray(), null);
		assertNotNull(user);
		assertEquals("user1", user.username);

		user = this.auth.authenticate("user2", "pass2".toCharArray(), null);
		assertNotNull(user);
		assertEquals("user2", user.username);

		// Test different encryptions
		user = this.auth.authenticate("plain", "passWord".toCharArray(), null);
		assertNotNull(user);
		assertEquals("plain", user.username);

		MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "false");
		user = this.auth.authenticate("crypt", "password".toCharArray(), null);
		assertNotNull(user);
		assertEquals("crypt", user.username);

		user = this.auth.authenticate("md5", "password".toCharArray(), null);
		assertNotNull(user);
		assertEquals("md5", user.username);

		user = this.auth.authenticate("sha", "password".toCharArray(), null);
		assertNotNull(user);
		assertEquals("sha", user.username);

		// Test leading and trailing whitespace
		user = this.auth.authenticate("trailing", "whitespace".toCharArray(), null);
		assertNotNull(user);
		assertEquals("trailing", user.username);

		user = this.auth.authenticate("tabbed", "frontAndBack".toCharArray(), null);
		assertNotNull(user);
		assertEquals("tabbed", user.username);

		user = this.auth.authenticate("leading", "whitespace".toCharArray(), null);
		assertNotNull(user);
		assertEquals("leading", user.username);
	}

	@Test
	public void testAttributes() {
		MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "true");
		UserModel user = this.htpasswd.authenticate("user1", "pass1".toCharArray());
		assertNotNull(user);
		assertEquals("El Capitan", user.displayName);
		assertEquals("cheffe@example.com", user.emailAddress);
		assertTrue(user.canAdmin);

		user = this.htpasswd.authenticate("user2", "pass2".toCharArray());
		assertNotNull(user);
		assertEquals("User Two", user.displayName);
		assertTrue(user.canCreate);
		assertTrue(user.canFork);
	}

	@Test
	public void testAuthenticateDenied() {
		UserModel user = null;
		MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "true");
		user = this.htpasswd.authenticate("user1", "".toCharArray());
		assertNull("User 'user1' falsely authenticated.", user);

		user = this.htpasswd.authenticate("user1", "pass2".toCharArray());
		assertNull("User 'user1' falsely authenticated.", user);

		user = this.htpasswd.authenticate("user2", "lalala".toCharArray());
		assertNull("User 'user2' falsely authenticated.", user);

		user = this.htpasswd.authenticate("user3", "disabled".toCharArray());
		assertNull("User 'user3' falsely authenticated.", user);

		user = this.htpasswd.authenticate("user4", "disabled".toCharArray());
		assertNull("User 'user4' falsely authenticated.", user);

		user = this.htpasswd.authenticate("plain", "text".toCharArray());
		assertNull("User 'plain' falsely authenticated.", user);

		user = this.htpasswd.authenticate("plain", "password".toCharArray());
		assertNull("User 'plain' falsely authenticated.", user);

		MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "false");

		user = this.htpasswd.authenticate("crypt", "".toCharArray());
		assertNull("User 'cyrpt' falsely authenticated.", user);

		user = this.htpasswd.authenticate("crypt", "passwd".toCharArray());
		assertNull("User 'crypt' falsely authenticated.", user);

		user = this.htpasswd.authenticate("md5", "".toCharArray());
		assertNull("User 'md5' falsely authenticated.", user);

		user = this.htpasswd.authenticate("md5", "pwd".toCharArray());
		assertNull("User 'md5' falsely authenticated.", user);

		user = this.htpasswd.authenticate("sha", "".toCharArray());
		assertNull("User 'sha' falsely authenticated.", user);

		user = this.htpasswd.authenticate("sha", "letmein".toCharArray());
		assertNull("User 'sha' falsely authenticated.", user);

		user = this.htpasswd.authenticate("  tabbed", "frontAndBack".toCharArray());
		assertNull("User 'tabbed' falsely authenticated.", user);

		user = this.htpasswd.authenticate("    leading", "whitespace".toCharArray());
		assertNull("User 'leading' falsely authenticated.", user);
	}

	@Test
	public void testAuthenticationMangerDenied() {
		UserModel user = null;
		MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "true");
		user = this.auth.authenticate("user1", "".toCharArray(), null);
		assertNull("User 'user1' falsely authenticated.", user);

		user = this.auth.authenticate("user1", "pass2".toCharArray(), null);
		assertNull("User 'user1' falsely authenticated.", user);

		user = this.auth.authenticate("user2", "lalala".toCharArray(), null);
		assertNull("User 'user2' falsely authenticated.", user);

		user = this.auth.authenticate("user3", "disabled".toCharArray(), null);
		assertNull("User 'user3' falsely authenticated.", user);

		user = this.auth.authenticate("user4", "disabled".toCharArray(), null);
		assertNull("User 'user4' falsely authenticated.", user);

		user = this.auth.authenticate("plain", "text".toCharArray(), null);
		assertNull("User 'plain' falsely authenticated.", user);

		user = this.auth.authenticate("plain", "password".toCharArray(), null);
		assertNull("User 'plain' falsely authenticated.", user);

		MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "false");

		user = this.auth.authenticate("crypt", "".toCharArray(), null);
		assertNull("User 'cyrpt' falsely authenticated.", user);

		user = this.auth.authenticate("crypt", "passwd".toCharArray(), null);
		assertNull("User 'crypt' falsely authenticated.", user);

		user = this.auth.authenticate("md5", "".toCharArray(), null);
		assertNull("User 'md5' falsely authenticated.", user);

		user = this.auth.authenticate("md5", "pwd".toCharArray(), null);
		assertNull("User 'md5' falsely authenticated.", user);

		user = this.auth.authenticate("sha", "".toCharArray(), null);
		assertNull("User 'sha' falsely authenticated.", user);

		user = this.auth.authenticate("sha", "letmein".toCharArray(), null);
		assertNull("User 'sha' falsely authenticated.", user);

		user = this.auth.authenticate("  tabbed", "frontAndBack".toCharArray(), null);
		assertNull("User 'tabbed' falsely authenticated.", user);

		user = this.auth.authenticate("    leading", "whitespace".toCharArray(), null);
		assertNull("User 'leading' falsely authenticated.", user);
	}

	@Test
	public void testCleartextIntrusion() {
		MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "true");
		assertNull(this.htpasswd.authenticate("md5",
				"$apr1$qAGGNfli$sAn14mn.WKId/3EQS7KSX0".toCharArray()));
		assertNull(this.htpasswd.authenticate("sha",
				"{SHA}W6ph5Mm5Pz8GgiULbPgzG37mj9g=".toCharArray()));

		assertNull(this.htpasswd.authenticate("user1", "#externalAccount".toCharArray()));

		MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "false");
		assertNull(this.htpasswd.authenticate("md5",
				"$apr1$qAGGNfli$sAn14mn.WKId/3EQS7KSX0".toCharArray()));
		assertNull(this.htpasswd.authenticate("sha",
				"{SHA}W6ph5Mm5Pz8GgiULbPgzG37mj9g=".toCharArray()));

		assertNull(this.htpasswd.authenticate("user1", "#externalAccount".toCharArray()));
	}

	@Test
	public void testCryptVsPlaintext() {
		MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "false");
		assertNull(this.htpasswd.authenticate("crypt", "6TmlbxqZ2kBIA".toCharArray()));
		assertNotNull(this.htpasswd.authenticate("crypt", "password".toCharArray()));

		MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "true");
		assertNotNull(this.htpasswd.authenticate("crypt", "6TmlbxqZ2kBIA".toCharArray()));
		assertNull(this.htpasswd.authenticate("crypt", "password".toCharArray()));
	}

	@Test
	public void testChangeHtpasswdFile() {
		UserModel user;

		// User default set up.
		user = this.htpasswd.authenticate("md5", "password".toCharArray());
		assertNotNull(user);
		assertEquals("md5", user.username);

		user = this.htpasswd.authenticate("sha", "password".toCharArray());
		assertNotNull(user);
		assertEquals("sha", user.username);

		user = this.htpasswd.authenticate("blueone", "GoBlue!".toCharArray());
		assertNull(user);

		user = this.htpasswd.authenticate("bluetwo", "YayBlue!".toCharArray());
		assertNull(user);

		// Switch to different htpasswd file.
		getSettings(RESOURCE_DIR + "htpasswd-user", null, null);

		user = this.htpasswd.authenticate("md5", "password".toCharArray());
		assertNull(user);

		user = this.htpasswd.authenticate("sha", "password".toCharArray());
		assertNull(user);

		user = this.htpasswd.authenticate("blueone", "GoBlue!".toCharArray());
		assertNotNull(user);
		assertEquals("blueone", user.username);

		user = this.htpasswd.authenticate("bluetwo", "YayBlue!".toCharArray());
		assertNotNull(user);
		assertEquals("bluetwo", user.username);
	}

	@Test
	public void testChangeHtpasswdFileNotExisting() {
		UserModel user;

		// User default set up.
		user = this.htpasswd.authenticate("md5", "password".toCharArray());
		assertNotNull(user);
		assertEquals("md5", user.username);

		user = this.htpasswd.authenticate("sha", "password".toCharArray());
		assertNotNull(user);
		assertEquals("sha", user.username);

		user = this.htpasswd.authenticate("blueone", "GoBlue!".toCharArray());
		assertNull(user);

		user = this.htpasswd.authenticate("bluetwo", "YayBlue!".toCharArray());
		assertNull(user);

		// Switch to different htpasswd file that doesn't exist.
		// Currently we stop working with old users upon this change.
		getSettings(RESOURCE_DIR + "no-such-file", null, null);

		user = this.htpasswd.authenticate("md5", "password".toCharArray());
		assertNull(user);

		user = this.htpasswd.authenticate("sha", "password".toCharArray());
		assertNull(user);

		user = this.htpasswd.authenticate("blueone", "GoBlue!".toCharArray());
		assertNull(user);

		user = this.htpasswd.authenticate("bluetwo", "YayBlue!".toCharArray());
		assertNull(user);
	}

}
