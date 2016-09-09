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
package com.gitblit.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import com.gitblit.models.FeedModel;

/**
 * Table model of a list of available feeds.
 *
 * @author James Moger
 *
 */
public class FeedsTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	List<FeedModel> list;

	enum Columns {
		Subscribed,
		Repository,
		Branch;

		@Override
		public String toString() {
			return name().replace('_', ' ');
		}
	}

	public FeedsTableModel() {
		this(new ArrayList<FeedModel>());
	}

	public FeedsTableModel(List<FeedModel> feeds) {
		this.list = feeds;
		Collections.sort(this.list);
	}

	@Override
	public int getRowCount() {
		return this.list.size();
	}

	@Override
	public int getColumnCount() {
		return Columns.values().length;
	}

	@Override
	public String getColumnName(int column) {
		final Columns col = Columns.values()[column];
		switch (col) {
		case Repository:
			return Translation.get("gb.repository");
		case Branch:
			return Translation.get("gb.branch");
		case Subscribed:
			break;
		}
		return "";
	}

	/**
	 * Returns <code>Object.class</code> regardless of <code>columnIndex</code>.
	 *
	 * @param columnIndex
	 *            the column being queried
	 * @return the Object.class
	 */
	@Override
	public Class<?> getColumnClass(int columnIndex) {
		final Columns col = Columns.values()[columnIndex];
		switch (col) {
		case Subscribed:
			return Boolean.class;
		case Branch:
		case Repository:
			break;
		}
		return String.class;
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		final Columns col = Columns.values()[columnIndex];
		switch (col) {
		case Subscribed:
			return true;
		case Branch:
		case Repository:
			break;
		}
		return false;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		final FeedModel model = this.list.get(rowIndex);
		final Columns col = Columns.values()[columnIndex];
		switch (col) {
		case Repository:
			return model.repository;
		case Branch:
			return model.branch;
		case Subscribed:
			return model.subscribed;
		}
		return null;
	}

	public FeedModel get(int modelRow) {
		return this.list.get(modelRow);
	}
}
