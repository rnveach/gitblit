package com.gitblit.models;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import com.gitblit.Constants.SearchObjectType;

/**
 * Model class that represents a search result.
 *
 * @author James Moger
 *
 */
public class SearchResult implements Serializable {

	private static final long serialVersionUID = 1L;

	public int hitId;

	public int totalHits;

	public float score;

	public Date date;

	public String author;

	public String committer;

	public String summary;

	public String fragment;

	public String repository;

	public String branch;

	public String commitId;

	public String path;

	public List<String> tags;

	public SearchObjectType type;

	public SearchResult() {
	}

	public String getId() {
		switch (this.type) {
		case blob:
			return this.path;
		case commit:
			return this.commitId;
		}
		return this.commitId;
	}

	@Override
	public String toString() {
		return this.score + " : " + this.type.name() + " : " + this.repository + " : " + getId()
				+ " (" + this.branch + ")";
	}
}