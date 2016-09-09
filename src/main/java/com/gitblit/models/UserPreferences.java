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
package com.gitblit.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.gitblit.Constants.Transport;
import com.gitblit.utils.StringUtils;

/**
 * User preferences.
 *
 * @author James Moger
 *
 */
public class UserPreferences implements Serializable {

	private static final long serialVersionUID = 1L;

	public final String username;

	private String locale;

	private Boolean emailMeOnMyTicketChanges;

	private Transport transport;

	private final Map<String, UserRepositoryPreferences> repositoryPreferences = new TreeMap<String, UserRepositoryPreferences>();

	public UserPreferences(String username) {
		this.username = username;
	}

	public Locale getLocale() {
		if (StringUtils.isEmpty(this.locale)) {
			return null;
		}
		final int underscore = this.locale.indexOf('_');
		if (underscore > 0) {
			final String lang = this.locale.substring(0, underscore);
			final String cc = this.locale.substring(underscore + 1);
			return new Locale(lang, cc);
		}
		return new Locale(this.locale);
	}

	public void setLocale(String locale) {
		this.locale = locale;
	}

	public UserRepositoryPreferences getRepositoryPreferences(String repositoryName) {
		final String key = repositoryName.toLowerCase();
		if (!this.repositoryPreferences.containsKey(key)) {
			// default preferences
			final UserRepositoryPreferences prefs = new UserRepositoryPreferences();
			prefs.username = this.username;
			prefs.repositoryName = repositoryName;
			this.repositoryPreferences.put(key, prefs);
		}
		return this.repositoryPreferences.get(key);
	}

	public void setRepositoryPreferences(UserRepositoryPreferences pref) {
		this.repositoryPreferences.put(pref.repositoryName.toLowerCase(), pref);
	}

	public boolean isStarredRepository(String repository) {
		if (this.repositoryPreferences == null) {
			return false;
		}
		final String key = repository.toLowerCase();
		if (this.repositoryPreferences.containsKey(key)) {
			final UserRepositoryPreferences pref = this.repositoryPreferences.get(key);
			return pref.starred;
		}
		return false;
	}

	public List<String> getStarredRepositories() {
		final List<String> list = new ArrayList<String>();
		for (final UserRepositoryPreferences prefs : this.repositoryPreferences.values()) {
			if (prefs.starred) {
				list.add(prefs.repositoryName);
			}
		}
		Collections.sort(list);
		return list;
	}

	public boolean isEmailMeOnMyTicketChanges() {
		if (this.emailMeOnMyTicketChanges == null) {
			return true;
		}
		return this.emailMeOnMyTicketChanges;
	}

	public void setEmailMeOnMyTicketChanges(boolean value) {
		this.emailMeOnMyTicketChanges = value;
	}

	public Transport getTransport() {
		return this.transport;
	}

	public void setTransport(Transport transport) {
		this.transport = transport;
	}
}
