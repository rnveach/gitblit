/*
 * Copyright 2014 gitblit.com.
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

import com.gitblit.utils.StringUtils;

/**
 * @author Alfred Schmid
 * @author James Moger
 *
 */
public class UserChoice implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String displayName;
	private final String userId;
	private final String email;

	/**
	 * Create a UserChoice without email and displayName.
	 *
	 * @param userId
	 *            the unique id of the user (in most cases the unique username
	 *            from user store). Can never be null or empty string.
	 *
	 */
	public UserChoice(String userId) {
		this(null, userId, null);
	}

	/**
	 * Create a UserChoice without email.
	 *
	 * @param displayName
	 *            the display name for the user. Can be null or empty string.
	 * @param userId
	 *            the unique id of the user (in most cases the unique username
	 *            from user store). Can never be null or empty string.
	 *
	 */
	public UserChoice(String displayName, String userId) {
		this(displayName, userId, null);
	}

	/**
	 * Create a UserChoice with email and displayName.
	 *
	 * @param displayName
	 *            the display name for the user. Can be null or empty string.
	 * @param userId
	 *            the unique id of the user (in most cases the unique username
	 *            from user store). Can never be null or empty string.
	 * @param email
	 *            the email from the user. Can be null or empty string.
	 *
	 */
	public UserChoice(String displayName, String userId, String email) {
		if (userId == null) {
			throw new IllegalArgumentException("The argument userId can't be null!");
		}
		if ("".equals(userId)) {
			throw new IllegalArgumentException("The argument userId can't be an empty String!");
		}
		this.displayName = displayName;
		this.userId = userId;
		this.email = email;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public String getDisplayNameOrUserId() {
		if (StringUtils.isEmpty(this.displayName)) {
			return this.userId;
		}
		return this.displayName;
	}

	public String getUserId() {
		return this.userId;
	}

	public String getEmail() {
		return this.email;
	}

	@Override
	public String toString() {
		final String dn = getDisplayNameOrUserId();
		if (dn.equals(this.userId)) {
			return dn;
		}
		return dn + " (" + this.userId + ")";
	}
}
