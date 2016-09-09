/*
 * Copyright 2015 gitblit.com.
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
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gitblit.Constants;

/**
 * A FilestoreModel represents a file stored outside a repository but referenced
 * by the repository using a unique objectID
 * 
 * @author Paul Martin
 *
 */
public class FilestoreModel implements Serializable, Comparable<FilestoreModel> {

	private static final long serialVersionUID = 1L;

	private static final String metaRegexText = new StringBuilder()
			.append("version\\shttps://git-lfs.github.com/spec/v1\\s+")
			.append("oid\\ssha256:(" + Constants.REGEX_SHA256 + ")\\s+").append("size\\s([0-9]+)")
			.toString();

	private static final Pattern metaRegex = Pattern.compile(metaRegexText);

	private static final int metaRegexIndexSHA = 1;

	private static final int metaRegexIndexSize = 2;

	public final String oid;

	private Long size;
	private Status status;

	// Audit
	private String stateChangedBy;
	private Date stateChangedOn;

	// Access Control
	private List<String> repositories;

	public FilestoreModel(String id, long definedSize) {
		this.oid = id;
		this.size = definedSize;
		this.status = Status.ReferenceOnly;
	}

	public FilestoreModel(String id, long expectedSize, UserModel user, String repo) {
		this.oid = id;
		this.size = expectedSize;
		this.status = Status.Upload_Pending;
		this.stateChangedBy = user.getName();
		this.stateChangedOn = new Date();
		this.repositories = new ArrayList<String>();
		this.repositories.add(repo);
	}

	/*
	 * Attempts to create a FilestoreModel from the given meta string
	 * 
	 * @return A valid FilestoreModel if successful, otherwise null
	 */
	public static FilestoreModel fromMetaString(String meta) {

		final Matcher m = metaRegex.matcher(meta);

		if (m.find()) {
			try {
				final Long size = Long.parseLong(m.group(metaRegexIndexSize));
				final String sha = m.group(metaRegexIndexSHA);
				return new FilestoreModel(sha, size);
			}
			catch (final Exception e) {
				// Fail silent - it is not a valid filestore item
			}
		}

		return null;
	}

	public synchronized long getSize() {
		return this.size;
	}

	public synchronized Status getStatus() {
		return this.status;
	}

	public synchronized String getChangedBy() {
		return this.stateChangedBy;
	}

	public synchronized Date getChangedOn() {
		return this.stateChangedOn;
	}

	public synchronized void setStatus(Status status, UserModel user) {
		this.status = status;
		this.stateChangedBy = user.getName();
		this.stateChangedOn = new Date();
	}

	public synchronized void reset(UserModel user, long size) {
		this.status = Status.Upload_Pending;
		this.stateChangedBy = user.getName();
		this.stateChangedOn = new Date();
		this.size = size;
	}

	/*
	 * Handles possible race condition with concurrent connections
	 * 
	 * @return true if action can proceed, false otherwise
	 */
	public synchronized boolean actionUpload(UserModel user) {
		if (this.status == Status.Upload_Pending) {
			this.status = Status.Upload_In_Progress;
			this.stateChangedBy = user.getName();
			this.stateChangedOn = new Date();
			return true;
		}

		return false;
	}

	public synchronized boolean isInErrorState() {
		return (this.status.value < 0);
	}

	public synchronized void addRepository(String repo) {
		if (this.status != Status.ReferenceOnly) {
			if (!this.repositories.contains(repo)) {
				this.repositories.add(repo);
			}
		}
	}

	public synchronized void removeRepository(String repo) {
		if (this.status != Status.ReferenceOnly) {
			this.repositories.remove(repo);
		}
	}

	public synchronized boolean isInRepositoryList(List<String> repoList) {
		if (this.status != Status.ReferenceOnly) {
			for (final String name : this.repositories) {
				if (repoList.contains(name)) {
					return true;
				}
			}
		}
		return false;
	}

	public static enum Status {

		ReferenceOnly(-42),

		Deleted(-30),
		AuthenticationRequired(-20),

		Error_Unknown(-8),
		Error_Unexpected_Stream_End(-7),
		Error_Invalid_Oid(-6),
		Error_Invalid_Size(-5),
		Error_Hash_Mismatch(-4),
		Error_Size_Mismatch(-3),
		Error_Exceeds_Size_Limit(-2),
		Error_Unauthorized(-1),
		// Negative values provide additional information and may be treated as
		// 0 when not required
		Unavailable(0),
		Upload_Pending(1),
		Upload_In_Progress(2),
		Available(3);

		final int value;

		Status(int value) {
			this.value = value;
		}

		public int getValue() {
			return this.value;
		}

		@Override
		public String toString() {
			return name().toLowerCase().replace('_', ' ');
		}

		public static Status fromState(int state) {
			for (final Status s : values()) {
				if (s.getValue() == state) {
					return s;
				}
			}
			throw new NoSuchElementException(String.valueOf(state));
		}
	}

	@Override
	public int compareTo(FilestoreModel o) {
		return this.oid.compareTo(o.oid);
	}

}
