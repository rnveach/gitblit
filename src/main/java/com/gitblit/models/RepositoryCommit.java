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
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Model class to represent a RevCommit, it's source repository, and the branch.
 * This class is used by the activity page.
 *
 * @author James Moger
 */
public class RepositoryCommit implements Serializable, Comparable<RepositoryCommit> {

	private static final long serialVersionUID = 1L;

	public final String repository;

	public final String branch;

	private final RevCommit commit;

	private List<RefModel> refs;

	public RepositoryCommit(String repository, String branch, RevCommit commit) {
		this.repository = repository;
		this.branch = branch;
		this.commit = commit;
	}

	public void setRefs(List<RefModel> refs) {
		this.refs = refs;
	}

	public List<RefModel> getRefs() {
		return this.refs;
	}

	public ObjectId getId() {
		return this.commit.getId();
	}

	public String getName() {
		return this.commit.getName();
	}

	public String getShortName() {
		return this.commit.getName().substring(0, 8);
	}

	public String getShortMessage() {
		return this.commit.getShortMessage();
	}

	public Date getCommitDate() {
		return new Date(this.commit.getCommitTime() * 1000L);
	}

	public int getParentCount() {
		return this.commit.getParentCount();
	}

	public RevCommit[] getParents() {
		return this.commit.getParents();
	}

	public PersonIdent getAuthorIdent() {
		return this.commit.getAuthorIdent();
	}

	public PersonIdent getCommitterIdent() {
		return this.commit.getCommitterIdent();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof RepositoryCommit) {
			final RepositoryCommit commit = (RepositoryCommit) o;
			return this.repository.equals(commit.repository) && getName().equals(commit.getName());
		}
		return false;
	}

	@Override
	public int hashCode() {
		return (this.repository + this.commit).hashCode();
	}

	@Override
	public int compareTo(RepositoryCommit o) {
		// reverse-chronological order
		if (this.commit.getCommitTime() > o.commit.getCommitTime()) {
			return -1;
		} else if (this.commit.getCommitTime() < o.commit.getCommitTime()) {
			return 1;
		}
		return 0;
	}

	public RepositoryCommit clone(String withRef) {
		return new RepositoryCommit(this.repository, withRef, this.commit);
	}

	@Override
	public String toString() {
		return MessageFormat.format("{0} {1} {2,date,yyyy-MM-dd HH:mm} {3} {4}", getShortName(),
				this.branch, getCommitterIdent().getWhen(), getAuthorIdent().getName(),
				getShortMessage());
	}
}