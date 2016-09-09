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
package com.gitblit.tickets;

import com.gitblit.utils.StringUtils;

/**
 * A Lucene query builder.
 *
 * @author James Moger
 *
 */
public class QueryBuilder {

	private final QueryBuilder parent;
	private String q;
	private transient StringBuilder sb;
	private int opCount;

	public static QueryBuilder q(String kernel) {
		return new QueryBuilder(kernel);
	}

	private QueryBuilder(QueryBuilder parent) {
		this.sb = new StringBuilder();
		this.parent = parent;
	}

	public QueryBuilder() {
		this("");
	}

	public QueryBuilder(String query) {
		this.sb = new StringBuilder(query == null ? "" : query);
		this.parent = null;
	}

	public boolean containsField(String field) {
		return this.sb.toString().contains(field + ":");
	}

	/**
	 * Creates a new AND subquery. Make sure to call endSubquery to get return
	 * *this* query.
	 *
	 * e.g. field:something AND (subquery)
	 *
	 * @return a subquery
	 */
	public QueryBuilder andSubquery() {
		this.sb.append(" AND (");
		return new QueryBuilder(this);
	}

	/**
	 * Creates a new OR subquery. Make sure to call endSubquery to get return
	 * *this* query.
	 *
	 * e.g. field:something OR (subquery)
	 *
	 * @return a subquery
	 */
	public QueryBuilder orSubquery() {
		this.sb.append(" OR (");
		return new QueryBuilder(this);
	}

	/**
	 * Ends a subquery and returns the parent query.
	 *
	 * @return the parent query
	 */
	public QueryBuilder endSubquery() {
		this.q = this.sb.toString().trim();
		if (this.q.length() > 0) {
			this.parent.sb.append(this.q).append(')');
		}
		return this.parent;
	}

	/**
	 * Append an OR condition.
	 *
	 * @param condition
	 * @return
	 */
	public QueryBuilder or(String condition) {
		return op(condition, " OR ");
	}

	/**
	 * Append an AND condition.
	 *
	 * @param condition
	 * @return
	 */
	public QueryBuilder and(String condition) {
		return op(condition, " AND ");
	}

	/**
	 * Append an AND NOT condition.
	 *
	 * @param condition
	 * @return
	 */
	public QueryBuilder andNot(String condition) {
		return op(condition, " AND NOT ");
	}

	/**
	 * Nest this query as a subquery.
	 *
	 * e.g. field:something AND field2:something else ==> (field:something AND
	 * field2:something else)
	 *
	 * @return this query nested as a subquery
	 */
	public QueryBuilder toSubquery() {
		if (this.opCount > 1) {
			this.sb.insert(0, '(').append(')');
		}
		return this;
	}

	/**
	 * Nest this query as an AND subquery of the condition
	 *
	 * @param condition
	 * @return the query nested as an AND subquery of the specified condition
	 */
	public QueryBuilder subqueryOf(String condition) {
		if (!StringUtils.isEmpty(condition)) {
			toSubquery().and(condition);
		}
		return this;
	}

	/**
	 * Removes a condition from the query.
	 *
	 * @param condition
	 * @return the query
	 */
	public QueryBuilder remove(String condition) {
		final int start = this.sb.indexOf(condition);
		if (start == 0) {
			// strip first condition
			this.sb.replace(0, condition.length(), "");
		} else if (start > 1) {
			// locate condition in query
			final int space1 = this.sb.lastIndexOf(" ", start - 1);
			final int space0 = this.sb.lastIndexOf(" ", space1 - 1);
			if ((space0 > -1) && (space1 > -1)) {
				final String conjunction = this.sb.substring(space0, space1).trim();
				if ("OR".equals(conjunction) || "AND".equals(conjunction)) {
					// remove the conjunction
					this.sb.replace(space0, start + condition.length(), "");
				} else {
					// unknown conjunction
					this.sb.replace(start, start + condition.length(), "");
				}
			} else {
				this.sb.replace(start, start + condition.length(), "");
			}
		}
		return this;
	}

	/**
	 * Generate the return the Lucene query.
	 *
	 * @return the generated query
	 */
	public String build() {
		if (this.parent != null) {
			throw new IllegalAccessError("You can not build a subquery! endSubquery() instead!");
		}
		this.q = this.sb.toString().trim();

		// cleanup paranthesis
		while (this.q.contains("()")) {
			this.q = this.q.replace("()", "");
		}
		if (this.q.length() > 0) {
			if ((this.q.charAt(0) == '(') && (this.q.charAt(this.q.length() - 1) == ')')) {
				// query is wrapped by unnecessary paranthesis
				this.q = this.q.substring(1, this.q.length() - 1);
			}
		}
		if (this.q.startsWith("AND ")) {
			this.q = this.q.substring(3).trim();
		}
		if (this.q.startsWith("OR ")) {
			this.q = this.q.substring(2).trim();
		}
		return this.q;
	}

	private QueryBuilder op(String condition, String op) {
		this.opCount++;
		if (!StringUtils.isEmpty(condition)) {
			if (this.sb.length() != 0) {
				this.sb.append(op);
			}
			this.sb.append(condition);
		}
		return this;
	}

	@Override
	public String toString() {
		return this.sb.toString().trim();
	}
}