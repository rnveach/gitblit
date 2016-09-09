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

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableRowSorter;

import com.gitblit.models.FeedEntryModel;
import com.gitblit.models.FeedModel;
import com.gitblit.utils.StringUtils;

/**
 * RSS Feeds Panel displays recent entries and launches the browser to view the
 * commit. commitdiff, or tree of a commit.
 *
 * @author James Moger
 *
 */
public abstract class FeedsPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final GitblitClient gitblit;

	private final String ALL = "*";

	private FeedEntryTableModel tableModel;

	private TableRowSorter<FeedEntryTableModel> defaultSorter;

	private HeaderPanel header;

	private JTable table;

	private DefaultComboBoxModel<String> repositoryChoices;

	private JComboBox<String> repositorySelector;

	private DefaultComboBoxModel<String> authorChoices;

	private JComboBox<String> authorSelector;

	private int page;

	private JButton prev;

	private JButton next;

	public FeedsPanel(GitblitClient gitblit) {
		super();
		this.gitblit = gitblit;
		initialize();
	}

	private void initialize() {

		this.prev = new JButton("<");
		this.prev.setToolTipText(Translation.get("gb.pagePrevious"));
		this.prev.setEnabled(false);
		this.prev.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				refreshFeeds(--FeedsPanel.this.page);
			}
		});

		this.next = new JButton(">");
		this.next.setToolTipText(Translation.get("gb.pageNext"));
		this.next.setEnabled(false);
		this.next.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				refreshFeeds(++FeedsPanel.this.page);
			}
		});

		final JButton refreshFeeds = new JButton(Translation.get("gb.refresh"));
		refreshFeeds.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				refreshFeeds(0);
			}
		});

		final JButton viewCommit = new JButton(Translation.get("gb.view"));
		viewCommit.setEnabled(false);
		viewCommit.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				viewCommit();
			}
		});

		final JButton viewCommitDiff = new JButton(Translation.get("gb.commitdiff"));
		viewCommitDiff.setEnabled(false);
		viewCommitDiff.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				viewCommitDiff();
			}
		});

		final JButton viewTree = new JButton(Translation.get("gb.tree"));
		viewTree.setEnabled(false);
		viewTree.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				viewTree();
			}
		});

		final JButton subscribeFeeds = new JButton(Translation.get("gb.subscribe") + "...");
		subscribeFeeds.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				subscribeFeeds(FeedsPanel.this.gitblit.getAvailableFeeds());
			}
		});

		final JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, Utils.MARGIN, 0));
		controls.add(refreshFeeds);
		controls.add(subscribeFeeds);
		controls.add(viewCommit);
		controls.add(viewCommitDiff);
		controls.add(viewTree);

		final NameRenderer nameRenderer = new NameRenderer();
		this.tableModel = new FeedEntryTableModel();
		this.header = new HeaderPanel(Translation.get("gb.activity"), "feed_16x16.png");
		this.table = Utils.newTable(this.tableModel, Utils.DATE_FORMAT);
		this.defaultSorter = new TableRowSorter<FeedEntryTableModel>(this.tableModel);
		String name = this.table.getColumnName(FeedEntryTableModel.Columns.Author.ordinal());
		this.table.getColumn(name).setCellRenderer(nameRenderer);
		name = this.table.getColumnName(FeedEntryTableModel.Columns.Repository.ordinal());
		this.table.getColumn(name).setCellRenderer(nameRenderer);

		name = this.table.getColumnName(FeedEntryTableModel.Columns.Branch.ordinal());
		this.table.getColumn(name).setCellRenderer(new BranchRenderer<String>());

		name = this.table.getColumnName(FeedEntryTableModel.Columns.Message.ordinal());
		this.table.getColumn(name).setCellRenderer(new MessageRenderer(this.gitblit));

		this.table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					if (e.isControlDown()) {
						viewCommitDiff();
					} else {
						viewCommit();
					}
				}
			}
		});

		this.table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) {
					return;
				}
				final boolean singleSelection = FeedsPanel.this.table.getSelectedRowCount() == 1;
				viewCommit.setEnabled(singleSelection);
				viewCommitDiff.setEnabled(singleSelection);
				viewTree.setEnabled(singleSelection);
			}
		});

		this.repositoryChoices = new DefaultComboBoxModel<String>();
		this.repositorySelector = new JComboBox<String>(this.repositoryChoices);
		this.repositorySelector.setRenderer(nameRenderer);
		this.repositorySelector.setForeground(nameRenderer.getForeground());
		this.repositorySelector.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				// repopulate the author list based on repository selection
				// preserve author selection, if possible
				String selectedAuthor = null;
				if (FeedsPanel.this.authorSelector.getSelectedIndex() > -1) {
					selectedAuthor = FeedsPanel.this.authorSelector.getSelectedItem().toString();
				}
				updateAuthors();
				if (selectedAuthor != null) {
					if (FeedsPanel.this.authorChoices.getIndexOf(selectedAuthor) > -1) {
						FeedsPanel.this.authorChoices.setSelectedItem(selectedAuthor);
					}
				}
				filterFeeds();
			}
		});
		this.authorChoices = new DefaultComboBoxModel<String>();
		this.authorSelector = new JComboBox<String>(this.authorChoices);
		this.authorSelector.setRenderer(nameRenderer);
		this.authorSelector.setForeground(nameRenderer.getForeground());
		this.authorSelector.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				filterFeeds();
			}
		});
		final JPanel northControls = new JPanel(new FlowLayout(FlowLayout.LEFT, Utils.MARGIN, 0));
		northControls.add(new JLabel(Translation.get("gb.repository")));
		northControls.add(this.repositorySelector);
		northControls.add(new JLabel(Translation.get("gb.author")));
		northControls.add(this.authorSelector);
		// northControls.add(prev);
		// northControls.add(next);

		final JPanel northPanel = new JPanel(new BorderLayout(0, Utils.MARGIN));
		northPanel.add(this.header, BorderLayout.NORTH);
		northPanel.add(northControls, BorderLayout.CENTER);

		setLayout(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		add(northPanel, BorderLayout.NORTH);
		add(new JScrollPane(this.table), BorderLayout.CENTER);
		add(controls, BorderLayout.SOUTH);
	}

	@Override
	public Insets getInsets() {
		return Utils.INSETS;
	}

	protected void refreshFeeds(final int page) {
		this.page = page;
		final GitblitWorker worker = new GitblitWorker(FeedsPanel.this, null) {
			@Override
			protected Boolean doRequest() throws IOException {
				FeedsPanel.this.gitblit.refreshSubscribedFeeds(page);
				return true;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
			}
		};
		worker.execute();
	}

	protected abstract void subscribeFeeds(List<FeedModel> feeds);

	protected void updateTable(boolean pack) {
		this.tableModel.entries.clear();
		this.tableModel.entries.addAll(this.gitblit.getSyndicatedEntries());
		this.tableModel.fireTableDataChanged();
		this.header.setText(Translation.get("gb.activity") + " ("
				+ this.gitblit.getSyndicatedEntries().size()
				+ (this.page > 0 ? (", pg " + (this.page + 1)) : "") + ")");
		if (pack) {
			Utils.packColumns(this.table, Utils.MARGIN);
		}
		this.table.scrollRectToVisible(new Rectangle(this.table.getCellRect(0, 0, true)));

		if (this.page == 0) {
			// determine unique repositories
			final Set<String> uniqueRepositories = new HashSet<String>();
			for (final FeedEntryModel entry : this.tableModel.entries) {
				uniqueRepositories.add(entry.repository);
			}

			// repositories
			final List<String> sortedRespositories = new ArrayList<String>(uniqueRepositories);
			StringUtils.sortRepositorynames(sortedRespositories);
			this.repositoryChoices.removeAllElements();
			this.repositoryChoices.addElement(this.ALL);
			for (final String repo : sortedRespositories) {
				this.repositoryChoices.addElement(repo);
			}
		}

		// update pagination buttons
		this.next.setEnabled(this.tableModel.entries.size() > 0);
		this.prev.setEnabled(this.page > 0);
	}

	private void updateAuthors() {
		String repository = this.ALL;
		if (this.repositorySelector.getSelectedIndex() > -1) {
			repository = this.repositorySelector.getSelectedItem().toString();
		}

		// determine unique repositories and authors
		final Set<String> uniqueAuthors = new HashSet<String>();
		for (final FeedEntryModel entry : this.tableModel.entries) {
			if (repository.equals(this.ALL) || entry.repository.equalsIgnoreCase(repository)) {
				uniqueAuthors.add(entry.author);
			}
		}
		// authors
		final List<String> sortedAuthors = new ArrayList<String>(uniqueAuthors);
		Collections.sort(sortedAuthors);
		this.authorChoices.removeAllElements();
		this.authorChoices.addElement(this.ALL);
		for (final String author : sortedAuthors) {
			this.authorChoices.addElement(author);
		}
	}

	protected FeedEntryModel getSelectedSyndicatedEntry() {
		final int viewRow = this.table.getSelectedRow();
		final int modelRow = this.table.convertRowIndexToModel(viewRow);
		final FeedEntryModel entry = this.tableModel.get(modelRow);
		return entry;
	}

	protected void viewCommit() {
		final FeedEntryModel entry = getSelectedSyndicatedEntry();
		Utils.browse(entry.link);
	}

	protected void viewCommitDiff() {
		final FeedEntryModel entry = getSelectedSyndicatedEntry();
		Utils.browse(entry.link.replace("/commit/", "/commitdiff/"));
	}

	protected void viewTree() {
		final FeedEntryModel entry = getSelectedSyndicatedEntry();
		Utils.browse(entry.link.replace("/commit/", "/tree/"));
	}

	protected void filterFeeds() {
		final String repository;
		if (this.repositorySelector.getSelectedIndex() > -1) {
			repository = this.repositorySelector.getSelectedItem().toString();
		} else {
			repository = this.ALL;
		}

		final String author;
		if (this.authorSelector.getSelectedIndex() > -1) {
			author = this.authorSelector.getSelectedItem().toString();
		} else {
			author = this.ALL;
		}

		if (repository.equals(this.ALL) && author.equals(this.ALL)) {
			this.table.setRowSorter(this.defaultSorter);
			return;
		}
		final int repositoryIndex = FeedEntryTableModel.Columns.Repository.ordinal();
		final int authorIndex = FeedEntryTableModel.Columns.Author.ordinal();
		RowFilter<FeedEntryTableModel, Object> containsFilter;
		if (repository.equals(this.ALL)) {
			// author filter
			containsFilter = new RowFilter<FeedEntryTableModel, Object>() {
				@Override
				public boolean include(Entry<? extends FeedEntryTableModel, ? extends Object> entry) {
					return entry.getStringValue(authorIndex).equalsIgnoreCase(author);
				}
			};
		} else if (author.equals(this.ALL)) {
			// repository filter
			containsFilter = new RowFilter<FeedEntryTableModel, Object>() {
				@Override
				public boolean include(Entry<? extends FeedEntryTableModel, ? extends Object> entry) {
					return entry.getStringValue(repositoryIndex).equalsIgnoreCase(repository);
				}
			};
		} else {
			// repository-author filter
			containsFilter = new RowFilter<FeedEntryTableModel, Object>() {
				@Override
				public boolean include(Entry<? extends FeedEntryTableModel, ? extends Object> entry) {
					final boolean authorMatch = entry.getStringValue(authorIndex).equalsIgnoreCase(
							author);
					final boolean repositoryMatch = entry.getStringValue(repositoryIndex)
							.equalsIgnoreCase(repository);
					return authorMatch && repositoryMatch;
				}
			};
		}
		final TableRowSorter<FeedEntryTableModel> sorter = new TableRowSorter<FeedEntryTableModel>(
				this.tableModel);
		sorter.setRowFilter(containsFilter);
		this.table.setRowSorter(sorter);
	}
}
