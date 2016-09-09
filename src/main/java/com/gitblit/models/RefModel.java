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

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;

import com.gitblit.utils.JGitUtils;

/**
 * RefModel is a serializable model class that represents a tag or branch and
 * includes the referenced object.
 *
 * @author James Moger
 *
 */
public class RefModel implements Serializable, Comparable<RefModel> {

	private static final long serialVersionUID = 1L;
	public final String displayName;
	public final RevObject referencedObject;
	public transient Ref reference;

	public RefModel(String displayName, Ref ref, RevObject refObject) {
		this.displayName = displayName;
		this.reference = ref;
		this.referencedObject = refObject;
	}

	public Date getDate() {
		Date date = new Date(0);
		if (this.referencedObject != null) {
			if (this.referencedObject instanceof RevTag) {
				final RevTag tag = (RevTag) this.referencedObject;
				final PersonIdent tagger = tag.getTaggerIdent();
				if (tagger != null) {
					date = tagger.getWhen();
				}
			} else if (this.referencedObject instanceof RevCommit) {
				final RevCommit commit = (RevCommit) this.referencedObject;
				date = JGitUtils.getAuthorDate(commit);
			}
		}
		return date;
	}

	public String getName() {
		if (this.reference == null) {
			return this.displayName;
		}
		return this.reference.getName();
	}

	public int getReferencedObjectType() {
		int type = this.referencedObject.getType();
		if (this.referencedObject instanceof RevTag) {
			type = ((RevTag) this.referencedObject).getObject().getType();
		}
		return type;
	}

	public ObjectId getReferencedObjectId() {
		if (this.referencedObject instanceof RevTag) {
			return ((RevTag) this.referencedObject).getObject().getId();
		}
		return this.referencedObject.getId();
	}

	public String getShortMessage() {
		String message = "";
		if (this.referencedObject instanceof RevTag) {
			message = ((RevTag) this.referencedObject).getShortMessage();
		} else if (this.referencedObject instanceof RevCommit) {
			message = ((RevCommit) this.referencedObject).getShortMessage();
		}
		return message;
	}

	public String getFullMessage() {
		String message = "";
		if (this.referencedObject instanceof RevTag) {
			message = ((RevTag) this.referencedObject).getFullMessage();
		} else if (this.referencedObject instanceof RevCommit) {
			message = ((RevCommit) this.referencedObject).getFullMessage();
		}
		return message;
	}

	public PersonIdent getAuthorIdent() {
		if (this.referencedObject instanceof RevTag) {
			return ((RevTag) this.referencedObject).getTaggerIdent();
		} else if (this.referencedObject instanceof RevCommit) {
			return ((RevCommit) this.referencedObject).getAuthorIdent();
		}
		return null;
	}

	public ObjectId getObjectId() {
		return this.reference.getObjectId();
	}

	public boolean isAnnotatedTag() {
		if (this.referencedObject instanceof RevTag) {
			return !getReferencedObjectId().equals(getObjectId());
		}
		return this.reference.getPeeledObjectId() != null;
	}

	@Override
	public int hashCode() {
		return getReferencedObjectId().hashCode() + getName().hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof RefModel) {
			final RefModel other = (RefModel) o;
			return getName().equals(other.getName());
		}
		return super.equals(o);
	}

	@Override
	public int compareTo(RefModel o) {
		return getDate().compareTo(o.getDate());
	}

	@Override
	public String toString() {
		return this.displayName;
	}
}