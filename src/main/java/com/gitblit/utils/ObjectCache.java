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
package com.gitblit.utils;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reusable coarse date-based object cache. The date precision is in
 * milliseconds and in fast, concurrent systems this cache is too simplistic.
 * However, for the cases where its being used in Gitblit this cache technique
 * is just fine.
 *
 * @author James Moger
 *
 */
public class ObjectCache<X> implements Serializable {

	private static final long serialVersionUID = 1L;

	private final Map<String, CachedObject<X>> cache = new ConcurrentHashMap<String, CachedObject<X>>();

	private class CachedObject<Y> {

		public final String name;

		private volatile Date date;

		private volatile Y object;

		CachedObject(String name) {
			this.name = name;
			this.date = new Date(0);
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + ": " + this.name;
		}
	}

	public void clear() {
		this.cache.clear();
	}

	public boolean hasCurrent(String name, Date date) {
		return this.cache.containsKey(name) && (this.cache.get(name).date.compareTo(date) == 0);
	}

	public Date getDate(String name) {
		return this.cache.get(name).date;
	}

	public X getObject(String name) {
		if (this.cache.containsKey(name)) {
			return this.cache.get(name).object;
		}
		return null;
	}

	public void updateObject(String name, X object) {
		this.updateObject(name, new Date(), object);
	}

	public void updateObject(String name, Date date, X object) {
		CachedObject<X> obj;
		if (this.cache.containsKey(name)) {
			obj = this.cache.get(name);
		} else {
			obj = new CachedObject<X>(name);
			this.cache.put(name, obj);
		}
		obj.date = date;
		obj.object = object;
	}

	public X remove(String name) {
		if (this.cache.containsKey(name)) {
			return this.cache.remove(name).object;
		}
		return null;
	}

	public int size() {
		return this.cache.size();
	}
}
