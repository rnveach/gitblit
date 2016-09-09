package com.gitblit.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

/**
 * Utils for handling path strings
 *
 */
public class PathUtils {

	private PathUtils() {
	}

	/**
	 * Compress paths containing no files
	 *
	 * @param paths
	 *            lines from `git ls-tree -r --name-only ${branch}`
	 * @return compressed paths
	 */
	public static List<String> compressPaths(final Iterable<String> paths) {

		final ArrayList<String> pathList = new ArrayList<>();
		final Map<String, List<String[]>> folderRoots = new LinkedHashMap<>();

		for (final String s : paths) {
			final String[] components = s.split("/");

			// File in current directory
			if (components.length == 1) {
				pathList.add(components[0]);

				// Directory path
			} else {
				List<String[]> rootedPaths = folderRoots.get(components[0]);
				if (rootedPaths == null) {
					rootedPaths = new ArrayList<>();
				}
				rootedPaths.add(components);
				folderRoots.put(components[0], rootedPaths);
			}
		}

		for (final String folder : folderRoots.keySet()) {
			final List<String[]> matchingPaths = folderRoots.get(folder);

			if (matchingPaths.size() == 1) {
				pathList.add(toStringPath(matchingPaths.get(0)));
			} else {
				pathList.add(longestCommonSequence(matchingPaths));
			}
		}
		return pathList;
	}

	/**
	 * Get last path component
	 *
	 *
	 * @param path
	 *            string path separated by slashes
	 * @return rightmost entry
	 */
	public static String getLastPathComponent(final String path) {
		return Iterables.getLast(Splitter.on("/").omitEmptyStrings().split(path), path);
	}

	private static String toStringPath(final String[] pathComponents) {
		final List<String> tmp = Arrays.asList(pathComponents);
		return Joiner.on('/').join(tmp.subList(0, tmp.size() - 1)) + '/';
	}

	private static String longestCommonSequence(final List<String[]> paths) {

		final StringBuilder path = new StringBuilder();

		for (int i = 0; i < paths.get(0).length; i++) {
			final String current = paths.get(0)[i];
			for (int j = 1; j < paths.size(); j++) {
				if (!current.equals(paths.get(j)[i])) {
					return path.toString();
				}
			}
			path.append(current);
			path.append('/');
		}
		return path.toString();
	}

}
