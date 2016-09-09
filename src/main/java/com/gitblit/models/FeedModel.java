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
package com.gitblit.models;

import java.io.Serializable;
import java.util.Date;

import com.gitblit.utils.StringUtils;

/**
 * FeedModel represents a syndication (RSS) feed.
 *
 * @author James Moger
 */
public class FeedModel implements Serializable, Comparable<FeedModel> {

	public String repository;
	public String branch;
	public Date lastRefreshDate;
	public Date currentRefreshDate;

	public boolean subscribed;

	private static final long serialVersionUID = 1L;

	public FeedModel() {
		this("");
		this.subscribed = false;
	}

	public FeedModel(String definition) {
		this.subscribed = true;
		this.lastRefreshDate = new Date(0);
		this.currentRefreshDate = new Date(0);

		final String[] fields = definition.split(":");
		this.repository = fields[0];
		if (fields.length > 1) {
			this.branch = fields[1];
		}
	}

	@Override
	public String toString() {
		if (StringUtils.isEmpty(this.branch)) {
			return this.repository;
		}
		return this.repository + ":" + this.branch;
	}

	@Override
	public int compareTo(FeedModel o) {
		final int repositoryCompare = StringUtils.compareRepositoryNames(this.repository,
				o.repository);
		if (repositoryCompare == 0) {
			// same repository
			if (StringUtils.isEmpty(this.branch)) {
				return 1;
			} else if (StringUtils.isEmpty(o.branch)) {
				return -1;
			}
			return this.branch.compareTo(o.branch);
		}
		return repositoryCompare;
	}

	@Override
	public int hashCode() {
		return toString().toLowerCase().hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof FeedModel) {
			return hashCode() == o.hashCode();
		}
		return false;
	}
}
