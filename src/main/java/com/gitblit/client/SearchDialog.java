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
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.gitblit.Constants;
import com.gitblit.Constants.SearchType;
import com.gitblit.models.FeedEntryModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.StringUtils;

/**
 * The search dialog allows searching of a repository branch. This matches the
 * search implementation of the site.
 *
 * @author James Moger
 *
 */
public class SearchDialog extends JFrame {

	private static final long serialVersionUID = 1L;

	private final boolean isSearch;

	private final GitblitClient gitblit;

	private FeedEntryTableModel tableModel;

	private HeaderPanel header;

	private JTable table;

	private JComboBox<RepositoryModel> repositorySelector;

	private DefaultComboBoxModel<String> branchChoices;

	private JComboBox<String> branchSelector;

	private JComboBox<SearchType> searchTypeSelector;

	private JTextField searchFragment;

	private JComboBox<Integer> maxHitsSelector;

	private int page;

	private JButton prev;

	private JButton next;

	public SearchDialog(GitblitClient gitblit, boolean isSearch) {
		super();
		this.gitblit = gitblit;
		this.isSearch = isSearch;
		setTitle(Translation.get(isSearch ? "gb.search" : "gb.log"));
		setIconImage(new ImageIcon(getClass().getResource("/gitblt-favicon.png")).getImage());
		initialize();
		setSize(900, 550);
	}

	private void initialize() {

		this.prev = new JButton("<");
		this.prev.setToolTipText(Translation.get("gb.pagePrevious"));
		this.prev.setEnabled(false);
		this.prev.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				search(--SearchDialog.this.page);
			}
		});

		this.next = new JButton(">");
		this.next.setToolTipText(Translation.get("gb.pageNext"));
		this.next.setEnabled(false);
		this.next.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				search(++SearchDialog.this.page);
			}
		});

		final JButton search = new JButton(Translation.get(this.isSearch ? "gb.search"
				: "gb.refresh"));
		search.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				search(0);
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

		final JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, Utils.MARGIN, 0));
		controls.add(viewCommit);
		controls.add(viewCommitDiff);
		controls.add(viewTree);

		final NameRenderer nameRenderer = new NameRenderer();
		this.tableModel = new FeedEntryTableModel();
		this.header = new HeaderPanel(Translation.get(this.isSearch ? "gb.search" : "gb.log"),
				this.isSearch ? "search-icon.png" : "commit_changes_16x16.png");
		this.table = Utils.newTable(this.tableModel, Utils.DATE_FORMAT);

		String name = this.table.getColumnName(FeedEntryTableModel.Columns.Author.ordinal());
		this.table.getColumn(name).setCellRenderer(nameRenderer);
		name = this.table.getColumnName(FeedEntryTableModel.Columns.Repository.ordinal());
		this.table.getColumn(name).setCellRenderer(nameRenderer);

		name = this.table.getColumnName(FeedEntryTableModel.Columns.Branch.ordinal());
		this.table.getColumn(name).setCellRenderer(new BranchRenderer<String>());

		name = this.table.getColumnName(FeedEntryTableModel.Columns.Message.ordinal());
		this.table.getColumn(name).setCellRenderer(new MessageRenderer());

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
				final boolean singleSelection = SearchDialog.this.table.getSelectedRowCount() == 1;
				viewCommit.setEnabled(singleSelection);
				viewCommitDiff.setEnabled(singleSelection);
				viewTree.setEnabled(singleSelection);
			}
		});

		this.repositorySelector = new JComboBox<RepositoryModel>(this.gitblit.getRepositories()
				.toArray(new RepositoryModel[0]));
		this.repositorySelector.setRenderer(nameRenderer);
		this.repositorySelector.setForeground(nameRenderer.getForeground());
		this.repositorySelector.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				// repopulate the branch list based on repository selection
				// preserve branch selection, if possible
				String selectedBranch = null;
				if (SearchDialog.this.branchSelector.getSelectedIndex() > -1) {
					selectedBranch = SearchDialog.this.branchSelector.getSelectedItem().toString();
				}
				updateBranches();
				if (StringUtils.isEmpty(selectedBranch)) {
					// do not select branch
					SearchDialog.this.branchSelector.setSelectedIndex(-1);
				} else {
					if (SearchDialog.this.branchChoices.getIndexOf(selectedBranch) > -1) {
						// select branch
						SearchDialog.this.branchChoices.setSelectedItem(selectedBranch);
					} else {
						// branch does not exist, do not select branch
						SearchDialog.this.branchSelector.setSelectedIndex(-1);
					}
				}
			}
		});

		this.branchChoices = new DefaultComboBoxModel<String>();
		this.branchSelector = new JComboBox<String>(this.branchChoices);
		this.branchSelector.setRenderer(new BranchRenderer<String>());

		this.searchTypeSelector = new JComboBox<SearchType>(Constants.SearchType.values());
		this.searchTypeSelector.setSelectedItem(Constants.SearchType.COMMIT);

		this.maxHitsSelector = new JComboBox<Integer>(new Integer[] { 25, 50, 75, 100 });
		this.maxHitsSelector.setSelectedIndex(0);

		this.searchFragment = new JTextField(25);
		this.searchFragment.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				search(0);
			}
		});

		final JPanel queryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, Utils.MARGIN, 0));
		queryPanel.add(new JLabel(Translation.get("gb.repository")));
		queryPanel.add(this.repositorySelector);
		queryPanel.add(new JLabel(Translation.get("gb.branch")));
		queryPanel.add(this.branchSelector);
		if (this.isSearch) {
			queryPanel.add(new JLabel(Translation.get("gb.type")));
			queryPanel.add(this.searchTypeSelector);
		}
		queryPanel.add(new JLabel(Translation.get("gb.maxHits")));
		queryPanel.add(this.maxHitsSelector);

		final JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, Utils.MARGIN, 0));
		actionsPanel.add(search);
		actionsPanel.add(this.prev);
		actionsPanel.add(this.next);

		final JPanel northControls = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		northControls.add(queryPanel, BorderLayout.WEST);
		if (this.isSearch) {
			northControls.add(this.searchFragment, BorderLayout.CENTER);
		}
		northControls.add(actionsPanel, BorderLayout.EAST);

		final JPanel northPanel = new JPanel(new BorderLayout(0, Utils.MARGIN));
		northPanel.add(this.header, BorderLayout.NORTH);
		northPanel.add(northControls, BorderLayout.CENTER);

		final JPanel contentPanel = new JPanel() {

			private static final long serialVersionUID = 1L;

			@Override
			public Insets getInsets() {
				return Utils.INSETS;
			}
		};
		contentPanel.setLayout(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		contentPanel.add(northPanel, BorderLayout.NORTH);
		contentPanel.add(new JScrollPane(this.table), BorderLayout.CENTER);
		contentPanel.add(controls, BorderLayout.SOUTH);
		setLayout(new BorderLayout());
		add(contentPanel, BorderLayout.CENTER);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(WindowEvent event) {
				if (SearchDialog.this.isSearch) {
					SearchDialog.this.searchFragment.requestFocus();
				} else {
					search(0);
				}
			}

			@Override
			public void windowActivated(WindowEvent event) {
				if (SearchDialog.this.isSearch) {
					SearchDialog.this.searchFragment.requestFocus();
				}
			}
		});
	}

	public void selectRepository(RepositoryModel repository) {
		this.repositorySelector.setSelectedItem(repository);
	}

	private void updateBranches() {
		String repository = null;
		if (this.repositorySelector.getSelectedIndex() > -1) {
			repository = this.repositorySelector.getSelectedItem().toString();
		}
		final List<String> branches = this.gitblit.getBranches(repository);
		this.branchChoices.removeAllElements();
		for (final String branch : branches) {
			this.branchChoices.addElement(branch);
		}
	}

	protected void search(final int page) {
		this.page = page;
		final String repository = this.repositorySelector.getSelectedItem().toString();
		final String branch = this.branchSelector.getSelectedIndex() > -1 ? this.branchSelector
				.getSelectedItem().toString() : null;
		final Constants.SearchType searchType = (Constants.SearchType) this.searchTypeSelector
				.getSelectedItem();
		final String fragment = this.isSearch ? this.searchFragment.getText() : null;
		final int maxEntryCount = this.maxHitsSelector.getSelectedIndex() > -1 ? ((Integer) this.maxHitsSelector
				.getSelectedItem()) : -1;

		if (this.isSearch && StringUtils.isEmpty(fragment)) {
			return;
		}
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		final SwingWorker<List<FeedEntryModel>, Void> worker = new SwingWorker<List<FeedEntryModel>, Void>() {
			@Override
			protected List<FeedEntryModel> doInBackground() throws IOException {
				if (SearchDialog.this.isSearch) {
					return SearchDialog.this.gitblit.search(repository, branch, fragment,
							searchType, maxEntryCount, page);
				} else {
					return SearchDialog.this.gitblit.log(repository, branch, maxEntryCount, page);
				}
			}

			@Override
			protected void done() {
				setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
				try {
					final List<FeedEntryModel> results = get();
					if (SearchDialog.this.isSearch) {
						updateTable(true, fragment, results);
					} else {
						updateTable(true, branch == null ? "" : branch, results);
					}
				}
				catch (final Throwable t) {
					Utils.showException(SearchDialog.this, t);
				}
			}
		};
		worker.execute();
	}

	protected void updateTable(boolean pack, String text, List<FeedEntryModel> entries) {
		this.tableModel.entries.clear();
		this.tableModel.entries.addAll(entries);
		this.tableModel.fireTableDataChanged();
		setTitle(Translation.get(this.isSearch ? "gb.search" : "gb.log")
				+ (StringUtils.isEmpty(text) ? "" : (": " + text)) + " (" + entries.size()
				+ (this.page > 0 ? (", pg " + (this.page + 1)) : "") + ")");
		this.header.setText(getTitle());
		if (pack) {
			Utils.packColumns(this.table, Utils.MARGIN);
		}
		this.table.scrollRectToVisible(new Rectangle(this.table.getCellRect(0, 0, true)));

		// update pagination buttons
		final int maxHits = (Integer) this.maxHitsSelector.getSelectedItem();
		this.next.setEnabled(entries.size() == maxHits);
		this.prev.setEnabled(this.page > 0);
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
}
