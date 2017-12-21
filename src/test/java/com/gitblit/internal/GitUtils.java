package com.gitblit.internal;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public class GitUtils {
	private static final Collection<Repository> TEMP_REPOSITORIES = new LinkedList<>();

	private GitUtils() {
	}

	public static Repository createNewRepository() throws IOException {
		final File repoDir = File.createTempFile("TestTempRepository", "");
		if (!repoDir.delete()) {
			throw new IOException("Could not delete temporary file " + repoDir);
		}
		final Repository repository = FileRepositoryBuilder.create(new File(
				repoDir, ".git"));
		repository.create();
		TEMP_REPOSITORIES.add(repository);
		return repository;
	}

	public static File addAnEmptyFileAndCommit(Repository repository,
			String fileName) throws IOException, GitAPIException {
		try (Git git = new Git(repository)) {
			final File file = new File(repository.getDirectory().getParent(),
					fileName);
			if (!file.getParentFile().exists()
					&& !file.getParentFile().mkdirs()) {
				throw new IOException("Could not create directory "
						+ file.getParentFile());
			}
			if (!file.createNewFile()) {
				throw new IOException("Could not create file " + file);
			}
			git.add().addFilepattern(fileName).call();
			git.commit().setMessage("add " + fileName).call();
			return file;
		}
	}

	public static void clearTempRepositories() throws IOException {
		for (Repository repository : TEMP_REPOSITORIES) {
			FileUtils
					.deleteDirectory(repository.getDirectory().getParentFile());
		}
	}
}
