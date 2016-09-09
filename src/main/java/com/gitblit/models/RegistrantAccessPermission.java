/*
 * Copyright 2012 gitblit.com.
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

import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.PermissionType;
import com.gitblit.Constants.RegistrantType;
import com.gitblit.utils.StringUtils;

/**
 * Represents a Registrant-AccessPermission tuple.
 *
 * @author James Moger
 */
public class RegistrantAccessPermission implements Serializable,
		Comparable<RegistrantAccessPermission> {

	private static final long serialVersionUID = 1L;

	public String registrant;
	public AccessPermission permission;
	public RegistrantType registrantType;
	public PermissionType permissionType;
	public boolean mutable;
	public String source;

	public RegistrantAccessPermission() {
	}

	public RegistrantAccessPermission(RegistrantType registrantType) {
		this.registrantType = registrantType;
		this.permissionType = PermissionType.EXPLICIT;
		this.mutable = true;
	}

	public RegistrantAccessPermission(String registrant, AccessPermission permission,
			PermissionType permissionType, RegistrantType registrantType, String source,
			boolean mutable) {
		this.registrant = registrant;
		this.permission = permission;
		this.permissionType = permissionType;
		this.registrantType = registrantType;
		this.source = source;
		this.mutable = mutable;
	}

	public boolean isAdmin() {
		return PermissionType.ADMINISTRATOR.equals(this.permissionType);
	}

	public boolean isOwner() {
		return PermissionType.OWNER.equals(this.permissionType);
	}

	public boolean isExplicit() {
		return PermissionType.EXPLICIT.equals(this.permissionType);
	}

	public boolean isRegex() {
		return PermissionType.REGEX.equals(this.permissionType);
	}

	public boolean isTeam() {
		return PermissionType.TEAM.equals(this.permissionType);
	}

	public boolean isMissing() {
		return PermissionType.MISSING.equals(this.permissionType);
	}

	public int getScore() {
		switch (this.registrantType) {
		case REPOSITORY:
			if (isAdmin()) {
				return 0;
			}
			if (isOwner()) {
				return 1;
			}
			if (isExplicit()) {
				return 2;
			}
			if (isRegex()) {
				return 3;
			}
			if (isTeam()) {
				return 4;
			}
			//$FALL-THROUGH$
		default:
			return 0;
		}
	}

	@Override
	public int compareTo(RegistrantAccessPermission p) {
		switch (this.registrantType) {
		case REPOSITORY:
			// repository permissions are sorted in score order
			// to convey the order in which permissions are tested
			final int score1 = getScore();
			final int score2 = p.getScore();
			if ((score1 <= 2) && (score2 <= 2)) {
				// group admin, owner, and explicit together
				return StringUtils.compareRepositoryNames(this.registrant, p.registrant);
			}
			if (score1 < score2) {
				return -1;
			} else if (score2 < score1) {
				return 1;
			}
			return StringUtils.compareRepositoryNames(this.registrant, p.registrant);
		default:
			// user and team permissions are string sorted
			return this.registrant.toLowerCase().compareTo(p.registrant.toLowerCase());
		}
	}

	@Override
	public int hashCode() {
		return this.registrant.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof RegistrantAccessPermission) {
			final RegistrantAccessPermission p = (RegistrantAccessPermission) o;
			return this.registrant.equals(p.registrant);
		}

		return false;
	}

	@Override
	public String toString() {
		return this.permission.asRole(this.registrant);
	}
}