/*
 * Copyright 2012 gitblit.com.
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
package com.gitblit.service;

import static org.eclipse.jgit.treewalk.filter.TreeFilter.ANY_DIFF;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.DateTools.Resolution;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.highlight.Fragmenter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.SimpleSpanFragmenter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.SearchObjectType;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.models.RefModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.SearchResult;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;

/**
 * The Lucene service handles indexing and searching repositories.
 *
 * @author James Moger
 *
 */
public class LuceneService implements Runnable {

	private static final int INDEX_VERSION = 6;

	private static final String FIELD_OBJECT_TYPE = "type";
	private static final String FIELD_PATH = "path";
	private static final String FIELD_COMMIT = "commit";
	private static final String FIELD_BRANCH = "branch";
	private static final String FIELD_SUMMARY = "summary";
	private static final String FIELD_CONTENT = "content";
	private static final String FIELD_AUTHOR = "author";
	private static final String FIELD_COMMITTER = "committer";
	private static final String FIELD_DATE = "date";
	private static final String FIELD_TAG = "tag";

	private static final String CONF_FILE = "lucene.conf";
	private static final String LUCENE_DIR = "lucene";
	private static final String CONF_INDEX = "index";
	private static final String CONF_VERSION = "version";
	private static final String CONF_ALIAS = "aliases";
	private static final String CONF_BRANCH = "branches";

	private static final Version LUCENE_VERSION = Version.LUCENE_4_10_0;

	private final Logger logger = LoggerFactory.getLogger(LuceneService.class);

	private final IStoredSettings storedSettings;
	private final IRepositoryManager repositoryManager;
	private final File repositoriesFolder;

	private final Map<String, IndexSearcher> searchers = new ConcurrentHashMap<String, IndexSearcher>();
	private final Map<String, IndexWriter> writers = new ConcurrentHashMap<String, IndexWriter>();

	private final String luceneIgnoreExtensions = "7z arc arj bin bmp dll doc docx exe gif gz jar jpg lib lzh odg odf odt pdf ppt png so swf xcf xls xlsx zip";
	private Set<String> excludedExtensions;

	public LuceneService(IStoredSettings settings, IRepositoryManager repositoryManager) {

		this.storedSettings = settings;
		this.repositoryManager = repositoryManager;
		this.repositoriesFolder = repositoryManager.getRepositoriesFolder();
		String exts = this.luceneIgnoreExtensions;
		if (settings != null) {
			exts = settings.getString(Keys.web.luceneIgnoreExtensions, exts);
		}
		this.excludedExtensions = new TreeSet<String>(StringUtils.getStringsFromValue(exts));
	}

	/**
	 * Run is executed by the Gitblit executor service. Because this is called
	 * by an executor service, calls will queue - i.e. there can never be
	 * concurrent execution of repository index updates.
	 */
	@Override
	public void run() {
		if (!this.storedSettings.getBoolean(Keys.web.allowLuceneIndexing, true)) {
			// Lucene indexing is disabled
			return;
		}
		// reload the excluded extensions
		final String exts = this.storedSettings.getString(Keys.web.luceneIgnoreExtensions,
				this.luceneIgnoreExtensions);
		this.excludedExtensions = new TreeSet<String>(StringUtils.getStringsFromValue(exts));

		if (this.repositoryManager.isCollectingGarbage()) {
			// busy collecting garbage, try again later
			return;
		}

		for (final String repositoryName : this.repositoryManager.getRepositoryList()) {
			final RepositoryModel model = this.repositoryManager.getRepositoryModel(repositoryName);
			if (model.hasCommits && !ArrayUtils.isEmpty(model.indexedBranches)) {
				final Repository repository = this.repositoryManager.getRepository(model.name);
				if (repository == null) {
					if (this.repositoryManager.isCollectingGarbage(model.name)) {
						this.logger.info(MessageFormat.format(
								"Skipping Lucene index of {0}, busy garbage collecting",
								repositoryName));
					}
					continue;
				}
				index(model, repository);
				repository.close();
				System.gc();
			}
		}
	}

	/**
	 * Synchronously indexes a repository. This may build a complete index of a
	 * repository or it may update an existing index.
	 *
	 * @param displayName
	 *            the name of the repository
	 * @param repository
	 *            the repository object
	 */
	private void index(RepositoryModel model, Repository repository) {
		try {
			if (shouldReindex(repository)) {
				// (re)build the entire index
				final IndexResult result = reindex(model, repository);

				if (result.success) {
					if (result.commitCount > 0) {
						final String msg = "Built {0} Lucene index from {1} commits and {2} files across {3} branches in {4} secs";
						this.logger.info(MessageFormat.format(msg, model.name, result.commitCount,
								result.blobCount, result.branchCount, result.duration()));
					}
				} else {
					final String msg = "Could not build {0} Lucene index!";
					this.logger.error(MessageFormat.format(msg, model.name));
				}
			} else {
				// update the index with latest commits
				final IndexResult result = updateIndex(model, repository);
				if (result.success) {
					if (result.commitCount > 0) {
						final String msg = "Updated {0} Lucene index with {1} commits and {2} files across {3} branches in {4} secs";
						this.logger.info(MessageFormat.format(msg, model.name, result.commitCount,
								result.blobCount, result.branchCount, result.duration()));
					}
				} else {
					final String msg = "Could not update {0} Lucene index!";
					this.logger.error(MessageFormat.format(msg, model.name));
				}
			}
		}
		catch (final Throwable t) {
			this.logger.error(MessageFormat.format("Lucene indexing failure for {0}", model.name),
					t);
		}
	}

	/**
	 * Close the writer/searcher objects for a repository.
	 *
	 * @param repositoryName
	 */
	public synchronized void close(String repositoryName) {
		try {
			final IndexSearcher searcher = this.searchers.remove(repositoryName);
			if (searcher != null) {
				searcher.getIndexReader().close();
			}
		}
		catch (final Exception e) {
			this.logger.error("Failed to close index searcher for " + repositoryName, e);
		}

		try {
			final IndexWriter writer = this.writers.remove(repositoryName);
			if (writer != null) {
				writer.close();
			}
		}
		catch (final Exception e) {
			this.logger.error("Failed to close index writer for " + repositoryName, e);
		}
	}

	/**
	 * Close all Lucene indexers.
	 *
	 */
	public synchronized void close() {
		// close all writers
		for (final String writer : this.writers.keySet()) {
			try {
				this.writers.get(writer).close(true);
			}
			catch (final Throwable t) {
				this.logger.error("Failed to close Lucene writer for " + writer, t);
			}
		}
		this.writers.clear();

		// close all searchers
		for (final String searcher : this.searchers.keySet()) {
			try {
				this.searchers.get(searcher).getIndexReader().close();
			}
			catch (final Throwable t) {
				this.logger.error("Failed to close Lucene searcher for " + searcher, t);
			}
		}
		this.searchers.clear();
	}

	/**
	 * Deletes the Lucene index for the specified repository.
	 *
	 * @param repositoryName
	 * @return true, if successful
	 */
	public boolean deleteIndex(String repositoryName) {
		try {
			// close any open writer/searcher
			close(repositoryName);

			// delete the index folder
			final File repositoryFolder = FileKey.resolve(new File(this.repositoriesFolder,
					repositoryName), FS.DETECTED);
			final File luceneIndex = new File(repositoryFolder, LUCENE_DIR);
			if (luceneIndex.exists()) {
				org.eclipse.jgit.util.FileUtils.delete(luceneIndex,
						org.eclipse.jgit.util.FileUtils.RECURSIVE);
			}
			// delete the config file
			final File luceneConfig = new File(repositoryFolder, CONF_FILE);
			if (luceneConfig.exists()) {
				luceneConfig.delete();
			}
			return true;
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns the author for the commit, if this information is available.
	 *
	 * @param commit
	 * @return an author or unknown
	 */
	private static String getAuthor(RevCommit commit) {
		String name = "unknown";
		try {
			name = commit.getAuthorIdent().getName();
			if (StringUtils.isEmpty(name)) {
				name = commit.getAuthorIdent().getEmailAddress();
			}
		}
		catch (final NullPointerException n) {
		}
		return name;
	}

	/**
	 * Returns the committer for the commit, if this information is available.
	 *
	 * @param commit
	 * @return an committer or unknown
	 */
	private static String getCommitter(RevCommit commit) {
		String name = "unknown";
		try {
			name = commit.getCommitterIdent().getName();
			if (StringUtils.isEmpty(name)) {
				name = commit.getCommitterIdent().getEmailAddress();
			}
		}
		catch (final NullPointerException n) {
		}
		return name;
	}

	/**
	 * Get the tree associated with the given commit.
	 *
	 * @param walk
	 * @param commit
	 * @return tree
	 * @throws IOException
	 */
	private static RevTree getTree(final RevWalk walk, final RevCommit commit) throws IOException {
		final RevTree tree = commit.getTree();
		if (tree != null) {
			return tree;
		}
		walk.parseHeaders(commit);
		return commit.getTree();
	}

	/**
	 * Construct a keyname from the branch.
	 *
	 * @param branchName
	 * @return a keyname appropriate for the Git config file format
	 */
	private static String getBranchKey(String branchName) {
		return StringUtils.getSHA1(branchName);
	}

	/**
	 * Returns the Lucene configuration for the specified repository.
	 *
	 * @param repository
	 * @return a config object
	 */
	private static FileBasedConfig getConfig(Repository repository) {
		final File file = new File(repository.getDirectory(), CONF_FILE);
		final FileBasedConfig config = new FileBasedConfig(file, FS.detect());
		return config;
	}

	/**
	 * Reads the Lucene config file for the repository to check the index
	 * version. If the index version is different, then rebuild the repository
	 * index.
	 *
	 * @param repository
	 * @return true of the on-disk index format is different than INDEX_VERSION
	 */
	private static boolean shouldReindex(Repository repository) {
		try {
			final FileBasedConfig config = getConfig(repository);
			config.load();
			final int indexVersion = config.getInt(CONF_INDEX, CONF_VERSION, 0);
			// reindex if versions do not match
			return indexVersion != INDEX_VERSION;
		}
		catch (final Throwable t) {
		}
		return true;
	}

	/**
	 * This completely indexes the repository and will destroy any existing
	 * index.
	 *
	 * @param repositoryName
	 * @param repository
	 * @return IndexResult
	 */
	public IndexResult reindex(RepositoryModel model, Repository repository) {
		final IndexResult result = new IndexResult();
		if (!deleteIndex(model.name)) {
			return result;
		}
		try {
			final String[] encodings = this.storedSettings.getStrings(Keys.web.blobEncodings)
					.toArray(new String[0]);
			final FileBasedConfig config = getConfig(repository);
			final Set<String> indexedCommits = new TreeSet<String>();
			final IndexWriter writer = getIndexWriter(model.name);
			// build a quick lookup of tags
			final Map<String, List<String>> tags = new HashMap<String, List<String>>();
			for (final RefModel tag : JGitUtils.getTags(repository, false, -1)) {
				if (!tag.isAnnotatedTag()) {
					// skip non-annotated tags
					continue;
				}
				if (!tags.containsKey(tag.getReferencedObjectId().getName())) {
					tags.put(tag.getReferencedObjectId().getName(), new ArrayList<String>());
				}
				tags.get(tag.getReferencedObjectId().getName()).add(tag.displayName);
			}

			final ObjectReader reader = repository.newObjectReader();

			// get the local branches
			final List<RefModel> branches = JGitUtils.getLocalBranches(repository, true, -1);

			// sort them by most recently updated
			Collections.sort(branches, new Comparator<RefModel>() {
				@Override
				public int compare(RefModel ref1, RefModel ref2) {
					return ref2.getDate().compareTo(ref1.getDate());
				}
			});

			// reorder default branch to first position
			RefModel defaultBranch = null;
			final ObjectId defaultBranchId = JGitUtils.getDefaultBranch(repository);
			for (final RefModel branch : branches) {
				if (branch.getObjectId().equals(defaultBranchId)) {
					defaultBranch = branch;
					break;
				}
			}
			branches.remove(defaultBranch);
			branches.add(0, defaultBranch);

			// walk through each branch
			for (final RefModel branch : branches) {

				boolean indexBranch = false;
				if (model.indexedBranches.contains(com.gitblit.Constants.DEFAULT_BRANCH)
						&& branch.equals(defaultBranch)) {
					// indexing "default" branch
					indexBranch = true;
				} else if (branch.getName().startsWith(com.gitblit.Constants.R_META)) {
					// skip internal meta branches
					indexBranch = false;
				} else {
					// normal explicit branch check
					indexBranch = model.indexedBranches.contains(branch.getName());
				}

				// if this branch is not specifically indexed then skip
				if (!indexBranch) {
					continue;
				}

				final String branchName = branch.getName();
				final RevWalk revWalk = new RevWalk(reader);
				final RevCommit tip = revWalk.parseCommit(branch.getObjectId());
				revWalk.close();
				final String tipId = tip.getId().getName();

				final String keyName = getBranchKey(branchName);
				config.setString(CONF_ALIAS, null, keyName, branchName);
				config.setString(CONF_BRANCH, null, keyName, tipId);

				// index the blob contents of the tree
				final Map<String, ObjectId> paths = new TreeMap<String, ObjectId>();

				try (TreeWalk treeWalk = new TreeWalk(repository)) {
					treeWalk.addTree(tip.getTree());
					treeWalk.setRecursive(true);

					while (treeWalk.next()) {
						// ensure path is not in a submodule
						if (treeWalk.getFileMode(0) != FileMode.GITLINK) {
							paths.put(treeWalk.getPathString(), treeWalk.getObjectId(0));
						}
					}
				}

				final ByteArrayOutputStream os = new ByteArrayOutputStream();
				final byte[] tmp = new byte[32767];

				final RevWalk commitWalk = new RevWalk(reader);
				commitWalk.markStart(tip);

				RevCommit commit;
				while ((paths.size() > 0) && ((commit = commitWalk.next()) != null)) {
					try (TreeWalk diffWalk = new TreeWalk(reader)) {
						final int parentCount = commit.getParentCount();
						switch (parentCount) {
						case 0:
							diffWalk.addTree(new EmptyTreeIterator());
							break;
						case 1:
							diffWalk.addTree(getTree(commitWalk, commit.getParent(0)));
							break;
						default:
							// skip merge commits
							continue;
						}
						diffWalk.addTree(getTree(commitWalk, commit));
						diffWalk.setFilter(ANY_DIFF);
						diffWalk.setRecursive(true);
						while ((paths.size() > 0) && diffWalk.next()) {
							final String path = diffWalk.getPathString();
							if (!paths.containsKey(path)) {
								continue;
							}

							// remove path from set
							final ObjectId blobId = paths.remove(path);
							result.blobCount++;

							// index the blob metadata
							final String blobAuthor = getAuthor(commit);
							final String blobCommitter = getCommitter(commit);
							final String blobDate = DateTools.timeToString(
									commit.getCommitTime() * 1000L, Resolution.MINUTE);

							final Document doc = new Document();
							doc.add(new Field(FIELD_OBJECT_TYPE, SearchObjectType.blob.name(),
									StringField.TYPE_STORED));
							doc.add(new Field(FIELD_BRANCH, branchName, TextField.TYPE_STORED));
							doc.add(new Field(FIELD_COMMIT, commit.getName(), TextField.TYPE_STORED));
							doc.add(new Field(FIELD_PATH, path, TextField.TYPE_STORED));
							doc.add(new Field(FIELD_DATE, blobDate, StringField.TYPE_STORED));
							doc.add(new Field(FIELD_AUTHOR, blobAuthor, TextField.TYPE_STORED));
							doc.add(new Field(FIELD_COMMITTER, blobCommitter, TextField.TYPE_STORED));

							// determine extension to compare to the extension
							// blacklist
							String ext = null;
							final String name = path.toLowerCase();
							if (name.indexOf('.') > -1) {
								ext = name.substring(name.lastIndexOf('.') + 1);
							}

							// index the blob content
							if (StringUtils.isEmpty(ext) || !this.excludedExtensions.contains(ext)) {
								final ObjectLoader ldr = repository
										.open(blobId, Constants.OBJ_BLOB);
								final InputStream in = ldr.openStream();
								int n;
								while ((n = in.read(tmp)) > 0) {
									os.write(tmp, 0, n);
								}
								in.close();
								final byte[] content = os.toByteArray();
								final String str = StringUtils.decodeString(content, encodings);
								doc.add(new Field(FIELD_CONTENT, str, TextField.TYPE_STORED));
								os.reset();
							}

							// add the blob to the index
							writer.addDocument(doc);
						}
					}
				}

				os.close();

				// index the tip commit object
				if (indexedCommits.add(tipId)) {
					final Document doc = createDocument(tip, tags.get(tipId));
					doc.add(new Field(FIELD_BRANCH, branchName, TextField.TYPE_STORED));
					writer.addDocument(doc);
					result.commitCount += 1;
					result.branchCount += 1;
				}

				// traverse the log and index the previous commit objects
				try (RevWalk historyWalk = new RevWalk(reader)) {
					historyWalk.markStart(historyWalk.parseCommit(tip.getId()));
					RevCommit rev;
					while ((rev = historyWalk.next()) != null) {
						final String hash = rev.getId().getName();
						if (indexedCommits.add(hash)) {
							final Document doc = createDocument(rev, tags.get(hash));
							doc.add(new Field(FIELD_BRANCH, branchName, TextField.TYPE_STORED));
							writer.addDocument(doc);
							result.commitCount += 1;
						}
					}
				}

				revWalk.close();
			}

			// finished
			reader.close();

			// commit all changes and reset the searcher
			config.setInt(CONF_INDEX, null, CONF_VERSION, INDEX_VERSION);
			config.save();
			writer.commit();
			resetIndexSearcher(model.name);
			result.success();
		}
		catch (final Exception e) {
			this.logger.error("Exception while reindexing " + model.name, e);
		}
		return result;
	}

	/**
	 * Incrementally update the index with the specified commit for the
	 * repository.
	 *
	 * @param repositoryName
	 * @param repository
	 * @param branch
	 *            the fully qualified branch name (e.g. refs/heads/master)
	 * @param commit
	 * @return true, if successful
	 */
	private IndexResult index(String repositoryName, Repository repository, String branch,
			RevCommit commit) {
		final IndexResult result = new IndexResult();
		try {
			final String[] encodings = this.storedSettings.getStrings(Keys.web.blobEncodings)
					.toArray(new String[0]);
			final List<PathChangeModel> changedPaths = JGitUtils.getFilesInCommit(repository,
					commit);
			final String revDate = DateTools.timeToString(commit.getCommitTime() * 1000L,
					Resolution.MINUTE);
			final IndexWriter writer = getIndexWriter(repositoryName);
			for (final PathChangeModel path : changedPaths) {
				if (path.isSubmodule()) {
					continue;
				}
				// delete the indexed blob
				deleteBlob(repositoryName, branch, path.name);

				// re-index the blob
				if (!ChangeType.DELETE.equals(path.changeType)) {
					result.blobCount++;
					final Document doc = new Document();
					doc.add(new Field(FIELD_OBJECT_TYPE, SearchObjectType.blob.name(),
							StringField.TYPE_STORED));
					doc.add(new Field(FIELD_BRANCH, branch, TextField.TYPE_STORED));
					doc.add(new Field(FIELD_COMMIT, commit.getName(), TextField.TYPE_STORED));
					doc.add(new Field(FIELD_PATH, path.path, TextField.TYPE_STORED));
					doc.add(new Field(FIELD_DATE, revDate, StringField.TYPE_STORED));
					doc.add(new Field(FIELD_AUTHOR, getAuthor(commit), TextField.TYPE_STORED));
					doc.add(new Field(FIELD_COMMITTER, getCommitter(commit), TextField.TYPE_STORED));

					// determine extension to compare to the extension
					// blacklist
					String ext = null;
					final String name = path.name.toLowerCase();
					if (name.indexOf('.') > -1) {
						ext = name.substring(name.lastIndexOf('.') + 1);
					}

					if (StringUtils.isEmpty(ext) || !this.excludedExtensions.contains(ext)) {
						// read the blob content
						final String str = JGitUtils.getStringContent(repository, commit.getTree(),
								path.path, encodings);
						if (str != null) {
							doc.add(new Field(FIELD_CONTENT, str, TextField.TYPE_STORED));
							writer.addDocument(doc);
						}
					}
				}
			}
			writer.commit();

			// get any annotated commit tags
			final List<String> commitTags = new ArrayList<String>();
			for (final RefModel ref : JGitUtils.getTags(repository, false, -1)) {
				if (ref.isAnnotatedTag() && ref.getReferencedObjectId().equals(commit.getId())) {
					commitTags.add(ref.displayName);
				}
			}

			// create and write the Lucene document
			final Document doc = createDocument(commit, commitTags);
			doc.add(new Field(FIELD_BRANCH, branch, TextField.TYPE_STORED));
			result.commitCount++;
			result.success = index(repositoryName, doc);
		}
		catch (final Exception e) {
			this.logger.error(MessageFormat.format("Exception while indexing commit {0} in {1}",
					commit.getId().getName(), repositoryName), e);
		}
		return result;
	}

	/**
	 * Delete a blob from the specified branch of the repository index.
	 *
	 * @param repositoryName
	 * @param branch
	 * @param path
	 * @throws Exception
	 * @return true, if deleted, false if no record was deleted
	 */
	public boolean deleteBlob(String repositoryName, String branch, String path) throws Exception {
		final String pattern = MessageFormat.format(
				"{0}:'{'0} AND {1}:\"'{'1'}'\" AND {2}:\"'{'2'}'\"", FIELD_OBJECT_TYPE,
				FIELD_BRANCH, FIELD_PATH);
		final String q = MessageFormat.format(pattern, SearchObjectType.blob.name(), branch, path);

		final BooleanQuery query = new BooleanQuery();
		final StandardAnalyzer analyzer = new StandardAnalyzer(LUCENE_VERSION);
		final QueryParser qp = new QueryParser(LUCENE_VERSION, FIELD_SUMMARY, analyzer);
		query.add(qp.parse(q), Occur.MUST);

		final IndexWriter writer = getIndexWriter(repositoryName);
		final int numDocsBefore = writer.numDocs();
		writer.deleteDocuments(query);
		writer.commit();
		final int numDocsAfter = writer.numDocs();
		if (numDocsBefore == numDocsAfter) {
			this.logger.debug(MessageFormat.format("no records found to delete {0}",
					query.toString()));
			return false;
		} else {
			this.logger.debug(MessageFormat.format("deleted {0} records with {1}", numDocsBefore
					- numDocsAfter, query.toString()));
			return true;
		}
	}

	/**
	 * Updates a repository index incrementally from the last indexed commits.
	 *
	 * @param model
	 * @param repository
	 * @return IndexResult
	 */
	private IndexResult updateIndex(RepositoryModel model, Repository repository) {
		final IndexResult result = new IndexResult();
		try {
			final FileBasedConfig config = getConfig(repository);
			config.load();

			// build a quick lookup of annotated tags
			final Map<String, List<String>> tags = new HashMap<String, List<String>>();
			for (final RefModel tag : JGitUtils.getTags(repository, false, -1)) {
				if (!tag.isAnnotatedTag()) {
					// skip non-annotated tags
					continue;
				}
				if (!tags.containsKey(tag.getObjectId().getName())) {
					tags.put(tag.getReferencedObjectId().getName(), new ArrayList<String>());
				}
				tags.get(tag.getReferencedObjectId().getName()).add(tag.displayName);
			}

			// detect branch deletion
			// first assume all branches are deleted and then remove each
			// existing branch from deletedBranches during indexing
			final Set<String> deletedBranches = new TreeSet<String>();
			for (final String alias : config.getNames(CONF_ALIAS)) {
				final String branch = config.getString(CONF_ALIAS, null, alias);
				deletedBranches.add(branch);
			}

			// get the local branches
			final List<RefModel> branches = JGitUtils.getLocalBranches(repository, true, -1);

			// sort them by most recently updated
			Collections.sort(branches, new Comparator<RefModel>() {
				@Override
				public int compare(RefModel ref1, RefModel ref2) {
					return ref2.getDate().compareTo(ref1.getDate());
				}
			});

			// reorder default branch to first position
			RefModel defaultBranch = null;
			final ObjectId defaultBranchId = JGitUtils.getDefaultBranch(repository);
			for (final RefModel branch : branches) {
				if (branch.getObjectId().equals(defaultBranchId)) {
					defaultBranch = branch;
					break;
				}
			}
			branches.remove(defaultBranch);
			branches.add(0, defaultBranch);

			// walk through each branches
			for (final RefModel branch : branches) {
				final String branchName = branch.getName();

				boolean indexBranch = false;
				if (model.indexedBranches.contains(com.gitblit.Constants.DEFAULT_BRANCH)
						&& branch.equals(defaultBranch)) {
					// indexing "default" branch
					indexBranch = true;
				} else if (branch.getName().startsWith(com.gitblit.Constants.R_META)) {
					// ignore internal meta branches
					indexBranch = false;
				} else {
					// normal explicit branch check
					indexBranch = model.indexedBranches.contains(branch.getName());
				}

				// if this branch is not specifically indexed then skip
				if (!indexBranch) {
					continue;
				}

				// remove this branch from the deletedBranches set
				deletedBranches.remove(branchName);

				// determine last commit
				final String keyName = getBranchKey(branchName);
				final String lastCommit = config.getString(CONF_BRANCH, null, keyName);

				List<RevCommit> revs;
				if (StringUtils.isEmpty(lastCommit)) {
					// new branch/unindexed branch, get all commits on branch
					revs = JGitUtils.getRevLog(repository, branchName, 0, -1);
				} else {
					// pre-existing branch, get changes since last commit
					revs = JGitUtils.getRevLog(repository, lastCommit, branchName);
				}

				if (revs.size() > 0) {
					result.branchCount += 1;
				}

				// reverse the list of commits so we start with the first commit
				Collections.reverse(revs);
				for (final RevCommit commit : revs) {
					// index a commit
					result.add(index(model.name, repository, branchName, commit));
				}

				// update the config
				config.setInt(CONF_INDEX, null, CONF_VERSION, INDEX_VERSION);
				config.setString(CONF_ALIAS, null, keyName, branchName);
				config.setString(CONF_BRANCH, null, keyName, branch.getObjectId().getName());
				config.save();
			}

			// the deletedBranches set will normally be empty by this point
			// unless a branch really was deleted and no longer exists
			if (deletedBranches.size() > 0) {
				for (final String branch : deletedBranches) {
					final IndexWriter writer = getIndexWriter(model.name);
					writer.deleteDocuments(new Term(FIELD_BRANCH, branch));
					writer.commit();
				}
			}
			result.success = true;
		}
		catch (final Throwable t) {
			this.logger.error(
					MessageFormat.format("Exception while updating {0} Lucene index", model.name),
					t);
		}
		return result;
	}

	/**
	 * Creates a Lucene document for a commit
	 *
	 * @param commit
	 * @param tags
	 * @return a Lucene document
	 */
	private static Document createDocument(RevCommit commit, List<String> tags) {
		final Document doc = new Document();
		doc.add(new Field(FIELD_OBJECT_TYPE, SearchObjectType.commit.name(),
				StringField.TYPE_STORED));
		doc.add(new Field(FIELD_COMMIT, commit.getName(), TextField.TYPE_STORED));
		doc.add(new Field(FIELD_DATE, DateTools.timeToString(commit.getCommitTime() * 1000L,
				Resolution.MINUTE), StringField.TYPE_STORED));
		doc.add(new Field(FIELD_AUTHOR, getAuthor(commit), TextField.TYPE_STORED));
		doc.add(new Field(FIELD_COMMITTER, getCommitter(commit), TextField.TYPE_STORED));
		doc.add(new Field(FIELD_SUMMARY, commit.getShortMessage(), TextField.TYPE_STORED));
		doc.add(new Field(FIELD_CONTENT, commit.getFullMessage(), TextField.TYPE_STORED));
		if (!ArrayUtils.isEmpty(tags)) {
			doc.add(new Field(FIELD_TAG, StringUtils.flattenStrings(tags), TextField.TYPE_STORED));
		}
		return doc;
	}

	/**
	 * Incrementally index an object for the repository.
	 *
	 * @param repositoryName
	 * @param doc
	 * @return true, if successful
	 */
	private boolean index(String repositoryName, Document doc) {
		try {
			final IndexWriter writer = getIndexWriter(repositoryName);
			writer.addDocument(doc);
			writer.commit();
			resetIndexSearcher(repositoryName);
			return true;
		}
		catch (final Exception e) {
			this.logger.error(MessageFormat.format(
					"Exception while incrementally updating {0} Lucene index", repositoryName), e);
		}
		return false;
	}

	private static SearchResult createSearchResult(Document doc, float score, int hitId,
			int totalHits) throws ParseException {
		final SearchResult result = new SearchResult();
		result.hitId = hitId;
		result.totalHits = totalHits;
		result.score = score;
		result.date = DateTools.stringToDate(doc.get(FIELD_DATE));
		result.summary = doc.get(FIELD_SUMMARY);
		result.author = doc.get(FIELD_AUTHOR);
		result.committer = doc.get(FIELD_COMMITTER);
		result.type = SearchObjectType.fromName(doc.get(FIELD_OBJECT_TYPE));
		result.branch = doc.get(FIELD_BRANCH);
		result.commitId = doc.get(FIELD_COMMIT);
		result.path = doc.get(FIELD_PATH);
		if (doc.get(FIELD_TAG) != null) {
			result.tags = StringUtils.getStringsFromValue(doc.get(FIELD_TAG));
		}
		return result;
	}

	private synchronized void resetIndexSearcher(String repository) throws IOException {
		final IndexSearcher searcher = this.searchers.remove(repository);
		if (searcher != null) {
			searcher.getIndexReader().close();
		}
	}

	/**
	 * Gets an index searcher for the repository.
	 *
	 * @param repository
	 * @return
	 * @throws IOException
	 */
	private IndexSearcher getIndexSearcher(String repository) throws IOException {
		IndexSearcher searcher = this.searchers.get(repository);
		if (searcher == null) {
			final IndexWriter writer = getIndexWriter(repository);
			searcher = new IndexSearcher(DirectoryReader.open(writer, true));
			this.searchers.put(repository, searcher);
		}
		return searcher;
	}

	/**
	 * Gets an index writer for the repository. The index will be created if it
	 * does not already exist or if forceCreate is specified.
	 *
	 * @param repository
	 * @return an IndexWriter
	 * @throws IOException
	 */
	private IndexWriter getIndexWriter(String repository) throws IOException {
		IndexWriter indexWriter = this.writers.get(repository);
		final File repositoryFolder = FileKey.resolve(
				new File(this.repositoriesFolder, repository), FS.DETECTED);
		final File indexFolder = new File(repositoryFolder, LUCENE_DIR);
		final Directory directory = FSDirectory.open(indexFolder);

		if (indexWriter == null) {
			if (!indexFolder.exists()) {
				indexFolder.mkdirs();
			}
			final StandardAnalyzer analyzer = new StandardAnalyzer(LUCENE_VERSION);
			final IndexWriterConfig config = new IndexWriterConfig(LUCENE_VERSION, analyzer);
			config.setOpenMode(OpenMode.CREATE_OR_APPEND);
			indexWriter = new IndexWriter(directory, config);
			this.writers.put(repository, indexWriter);
		}
		return indexWriter;
	}

	/**
	 * Searches the specified repositories for the given text or query
	 *
	 * @param text
	 *            if the text is null or empty, null is returned
	 * @param page
	 *            the page number to retrieve. page is 1-indexed.
	 * @param pageSize
	 *            the number of elements to return for this page
	 * @param repositories
	 *            a list of repositories to search. if no repositories are
	 *            specified null is returned.
	 * @return a list of SearchResults in order from highest to the lowest score
	 *
	 */
	public List<SearchResult> search(String text, int page, int pageSize, List<String> repositories) {
		if (ArrayUtils.isEmpty(repositories)) {
			return null;
		}
		return search(text, page, pageSize, repositories.toArray(new String[0]));
	}

	/**
	 * Searches the specified repositories for the given text or query
	 *
	 * @param text
	 *            if the text is null or empty, null is returned
	 * @param page
	 *            the page number to retrieve. page is 1-indexed.
	 * @param pageSize
	 *            the number of elements to return for this page
	 * @param repositories
	 *            a list of repositories to search. if no repositories are
	 *            specified null is returned.
	 * @return a list of SearchResults in order from highest to the lowest score
	 *
	 */
	public List<SearchResult> search(String text, int page, int pageSize, String... repositories) {
		if (StringUtils.isEmpty(text)) {
			return null;
		}
		if (ArrayUtils.isEmpty(repositories)) {
			return null;
		}
		final Set<SearchResult> results = new LinkedHashSet<SearchResult>();
		final StandardAnalyzer analyzer = new StandardAnalyzer(LUCENE_VERSION);
		try {
			// default search checks summary and content
			final BooleanQuery query = new BooleanQuery();
			QueryParser qp;
			qp = new QueryParser(LUCENE_VERSION, FIELD_SUMMARY, analyzer);
			qp.setAllowLeadingWildcard(true);
			query.add(qp.parse(text), Occur.SHOULD);

			qp = new QueryParser(LUCENE_VERSION, FIELD_CONTENT, analyzer);
			qp.setAllowLeadingWildcard(true);
			query.add(qp.parse(text), Occur.SHOULD);

			IndexSearcher searcher;
			if (repositories.length == 1) {
				// single repository search
				searcher = getIndexSearcher(repositories[0]);
			} else {
				// multiple repository search
				final List<IndexReader> readers = new ArrayList<IndexReader>();
				for (final String repository : repositories) {
					final IndexSearcher repositoryIndex = getIndexSearcher(repository);
					readers.add(repositoryIndex.getIndexReader());
				}
				final IndexReader[] rdrs = readers.toArray(new IndexReader[readers.size()]);
				final MultiSourceReader reader = new MultiSourceReader(rdrs);
				searcher = new IndexSearcher(reader);
			}

			final Query rewrittenQuery = searcher.rewrite(query);
			this.logger.debug(rewrittenQuery.toString());

			final TopScoreDocCollector collector = TopScoreDocCollector.create(5000, true);
			searcher.search(rewrittenQuery, collector);
			final int offset = Math.max(0, (page - 1) * pageSize);
			final ScoreDoc[] hits = collector.topDocs(offset, pageSize).scoreDocs;
			final int totalHits = collector.getTotalHits();
			for (int i = 0; i < hits.length; i++) {
				final int docId = hits[i].doc;
				final Document doc = searcher.doc(docId);
				final SearchResult result = createSearchResult(doc, hits[i].score, offset + i + 1,
						totalHits);
				if (repositories.length == 1) {
					// single repository search
					result.repository = repositories[0];
				} else {
					// multi-repository search
					final MultiSourceReader reader = (MultiSourceReader) searcher.getIndexReader();
					final int index = reader.getSourceIndex(docId);
					result.repository = repositories[index];
				}
				final String content = doc.get(FIELD_CONTENT);
				result.fragment = getHighlightedFragment(analyzer, query, content, result);
				results.add(result);
			}
		}
		catch (final Exception e) {
			this.logger.error(MessageFormat.format("Exception while searching for {0}", text), e);
		}
		return new ArrayList<SearchResult>(results);
	}

	/**
	 *
	 * @param analyzer
	 * @param query
	 * @param content
	 * @param result
	 * @return
	 * @throws IOException
	 * @throws InvalidTokenOffsetsException
	 */
	private String getHighlightedFragment(Analyzer analyzer, Query query, String content,
			SearchResult result) throws IOException, InvalidTokenOffsetsException {
		if (content == null) {
			content = "";
		}

		final int tabLength = this.storedSettings.getInteger(Keys.web.tabLength, 4);
		final int fragmentLength = SearchObjectType.commit == result.type ? 512 : 150;

		final QueryScorer scorer = new QueryScorer(query, "content");
		final Fragmenter fragmenter = new SimpleSpanFragmenter(scorer, fragmentLength);

		// use an artificial delimiter for the token
		final String termTag = "!!--[";
		final String termTagEnd = "]--!!";
		final SimpleHTMLFormatter formatter = new SimpleHTMLFormatter(termTag, termTagEnd);
		final Highlighter highlighter = new Highlighter(formatter, scorer);
		highlighter.setTextFragmenter(fragmenter);

		String[] fragments = highlighter.getBestFragments(analyzer, "content", content, 3);
		if (ArrayUtils.isEmpty(fragments)) {
			if (SearchObjectType.blob == result.type) {
				return "";
			}
			// clip commit message
			String fragment = content;
			if (fragment.length() > fragmentLength) {
				fragment = fragment.substring(0, fragmentLength) + "...";
			}
			return "<pre class=\"text\">" + StringUtils.escapeForHtml(fragment, true, tabLength)
					+ "</pre>";
		}

		// make sure we have unique fragments
		final Set<String> uniqueFragments = new LinkedHashSet<String>();
		for (final String fragment : fragments) {
			uniqueFragments.add(fragment);
		}
		fragments = uniqueFragments.toArray(new String[uniqueFragments.size()]);

		final StringBuilder sb = new StringBuilder();
		for (int i = 0, len = fragments.length; i < len; i++) {
			String fragment = fragments[i];
			String tag = "<pre class=\"text\">";

			// resurrect the raw fragment from removing the artificial
			// delimiters
			final String raw = fragment.replace(termTag, "").replace(termTagEnd, "");

			// determine position of the raw fragment in the content
			final int pos = content.indexOf(raw);

			// restore complete first line of fragment
			int c = pos;
			while (c > 0) {
				c--;
				if (content.charAt(c) == '\n') {
					break;
				}
			}
			if (c > 0) {
				// inject leading chunk of first fragment line
				fragment = content.substring(c + 1, pos) + fragment;
			}

			if (SearchObjectType.blob == result.type) {
				// count lines as offset into the content for this fragment
				final int line = Math.max(1, StringUtils.countLines(content.substring(0, pos)));

				// create fragment tag with line number and language
				String lang = "";
				final String ext = StringUtils.getFileExtension(result.path).toLowerCase();
				if (!StringUtils.isEmpty(ext)) {
					// maintain leading space!
					lang = " lang-" + ext;
				}
				tag = MessageFormat.format("<pre class=\"prettyprint linenums:{0,number,0}{1}\">",
						line, lang);

			}

			sb.append(tag);

			// replace the artificial delimiter with html tags
			String html = StringUtils.escapeForHtml(fragment, false);
			html = html.replace(termTag, "<span class=\"highlight\">").replace(termTagEnd,
					"</span>");
			sb.append(html);
			sb.append("</pre>");
			if (i < (len - 1)) {
				sb.append("<span class=\"ellipses\">...</span><br/>");
			}
		}
		return sb.toString();
	}

	/**
	 * Simple class to track the results of an index update.
	 */
	private class IndexResult {
		long startTime = System.currentTimeMillis();
		long endTime = this.startTime;
		boolean success;
		int branchCount;
		int commitCount;
		int blobCount;

		void add(IndexResult result) {
			this.branchCount += result.branchCount;
			this.commitCount += result.commitCount;
			this.blobCount += result.blobCount;
		}

		void success() {
			this.success = true;
			this.endTime = System.currentTimeMillis();
		}

		float duration() {
			return (this.endTime - this.startTime) / 1000f;
		}
	}

	/**
	 * Custom subclass of MultiReader to identify the source index for a given
	 * doc id. This would not be necessary of there was a public method to
	 * obtain this information.
	 *
	 */
	private class MultiSourceReader extends MultiReader {

		MultiSourceReader(IndexReader[] readers) {
			super(readers, false);
		}

		int getSourceIndex(int docId) {
			int index = -1;
			try {
				index = super.readerIndex(docId);
			}
			catch (final Exception e) {
				LuceneService.this.logger.error("Error getting source index", e);
			}
			return index;
		}
	}
}
