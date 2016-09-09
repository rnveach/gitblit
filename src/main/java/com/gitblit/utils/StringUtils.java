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

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Utility class of string functions.
 *
 * @author James Moger
 *
 */
public class StringUtils {

	public static final String MD5_TYPE = "MD5:";

	public static final String COMBINED_MD5_TYPE = "CMD5:";

	/**
	 * Returns true if the string is null or empty.
	 *
	 * @param value
	 * @return true if string is null or empty
	 */
	public static boolean isEmpty(String value) {
		return (value == null) || (value.trim().length() == 0);
	}

	/**
	 * Replaces carriage returns and line feeds with html line breaks.
	 *
	 * @param string
	 * @return plain text with html line breaks
	 */
	public static String breakLinesForHtml(String string) {
		return string.replace("\r\n", "<br/>").replace("\r", "<br/>").replace("\n", "<br/>");
	}

	/**
	 * Prepare text for html presentation. Replace sensitive characters with
	 * html entities.
	 *
	 * @param inStr
	 * @param changeSpace
	 * @return plain text escaped for html
	 */
	public static String escapeForHtml(String inStr, boolean changeSpace) {
		return escapeForHtml(inStr, changeSpace, 4);
	}

	/**
	 * Prepare text for html presentation. Replace sensitive characters with
	 * html entities.
	 *
	 * @param inStr
	 * @param changeSpace
	 * @param tabLength
	 * @return plain text escaped for html
	 */
	public static String escapeForHtml(String inStr, boolean changeSpace, int tabLength) {
		final StringBuilder retStr = new StringBuilder();
		int i = 0;
		while (i < inStr.length()) {
			if (inStr.charAt(i) == '&') {
				retStr.append("&amp;");
			} else if (inStr.charAt(i) == '<') {
				retStr.append("&lt;");
			} else if (inStr.charAt(i) == '>') {
				retStr.append("&gt;");
			} else if (inStr.charAt(i) == '\"') {
				retStr.append("&quot;");
			} else if (changeSpace && (inStr.charAt(i) == ' ')) {
				retStr.append("&nbsp;");
			} else if (changeSpace && (inStr.charAt(i) == '\t')) {
				for (int j = 0; j < tabLength; j++) {
					retStr.append("&nbsp;");
				}
			} else {
				retStr.append(inStr.charAt(i));
			}
			i++;
		}
		return retStr.toString();
	}

	/**
	 * Decode html entities back into plain text characters.
	 *
	 * @param inStr
	 * @return returns plain text from html
	 */
	public static String decodeFromHtml(String inStr) {
		return inStr.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
				.replace("&quot;", "\"").replace("&nbsp;", " ");
	}

	/**
	 * Encodes a url parameter by escaping troublesome characters.
	 *
	 * @param inStr
	 * @return properly escaped url
	 */
	public static String encodeURL(String inStr) {
		final StringBuilder retStr = new StringBuilder();
		int i = 0;
		while (i < inStr.length()) {
			if (inStr.charAt(i) == '/') {
				retStr.append("%2F");
			} else if (inStr.charAt(i) == ' ') {
				retStr.append("%20");
			} else if (inStr.charAt(i) == '&') {
				retStr.append("%26");
			} else if (inStr.charAt(i) == '+') {
				retStr.append("%2B");
			} else {
				retStr.append(inStr.charAt(i));
			}
			i++;
		}
		return retStr.toString();
	}

	/**
	 * Flatten the list of strings into a single string with the specified
	 * separator.
	 *
	 * @param values
	 * @param separator
	 * @return flattened list
	 */
	public static String flattenStrings(String[] values, String separator) {
		return flattenStrings(Arrays.asList(values), separator);
	}

	/**
	 * Flatten the list of strings into a single string with a space separator.
	 *
	 * @param values
	 * @return flattened list
	 */
	public static String flattenStrings(Collection<String> values) {
		return flattenStrings(values, " ");
	}

	/**
	 * Flatten the list of strings into a single string with the specified
	 * separator.
	 *
	 * @param values
	 * @param separator
	 * @return flattened list
	 */
	public static String flattenStrings(Collection<String> values, String separator) {
		final StringBuilder sb = new StringBuilder();
		for (final String value : values) {
			sb.append(value).append(separator);
		}
		if (sb.length() > 0) {
			// truncate trailing separator
			sb.setLength(sb.length() - separator.length());
		}
		return sb.toString().trim();
	}

	/**
	 * Returns a string trimmed to a maximum length with trailing ellipses. If
	 * the string length is shorter than the max, the original string is
	 * returned.
	 *
	 * @param value
	 * @param max
	 * @return trimmed string
	 */
	public static String trimString(String value, int max) {
		if (value.length() <= max) {
			return value;
		}
		return value.substring(0, max - 3) + "...";
	}

	/**
	 * Left pad a string with the specified character, if the string length is
	 * less than the specified length.
	 *
	 * @param input
	 * @param length
	 * @param pad
	 * @return left-padded string
	 */
	public static String leftPad(String input, int length, char pad) {
		if (input.length() < length) {
			final StringBuilder sb = new StringBuilder();
			for (int i = 0, len = length - input.length(); i < len; i++) {
				sb.append(pad);
			}
			sb.append(input);
			return sb.toString();
		}
		return input;
	}

	/**
	 * Right pad a string with the specified character, if the string length is
	 * less then the specified length.
	 *
	 * @param input
	 * @param length
	 * @param pad
	 * @return right-padded string
	 */
	public static String rightPad(String input, int length, char pad) {
		if (input.length() < length) {
			final StringBuilder sb = new StringBuilder();
			sb.append(input);
			for (int i = 0, len = length - input.length(); i < len; i++) {
				sb.append(pad);
			}
			return sb.toString();
		}
		return input;
	}

	/**
	 * Calculates the SHA1 of the string.
	 *
	 * @param text
	 * @return sha1 of the string
	 */
	public static String getSHA1(String text) {
		try {
			final byte[] bytes = text.getBytes("iso-8859-1");
			return getSHA1(bytes);
		}
		catch (final UnsupportedEncodingException u) {
			throw new RuntimeException(u);
		}
	}

	/**
	 * Calculates the SHA1 of the byte array.
	 *
	 * @param bytes
	 * @return sha1 of the byte array
	 */
	public static String getSHA1(byte[] bytes) {
		try {
			final MessageDigest md = MessageDigest.getInstance("SHA-1");
			md.update(bytes, 0, bytes.length);
			final byte[] digest = md.digest();
			return toHex(digest);
		}
		catch (final NoSuchAlgorithmException t) {
			throw new RuntimeException(t);
		}
	}

	/**
	 * Calculates the MD5 of the string.
	 *
	 * @param string
	 * @return md5 of the string
	 */
	public static String getMD5(String string) {
		try {
			return getMD5(string.getBytes("iso-8859-1"));
		}
		catch (final UnsupportedEncodingException u) {
			throw new RuntimeException(u);
		}
	}

	/**
	 * Calculates the MD5 of the string.
	 *
	 * @param string
	 * @return md5 of the string
	 */
	public static String getMD5(byte[] bytes) {
		try {
			final MessageDigest md = MessageDigest.getInstance("MD5");
			md.reset();
			md.update(bytes);
			final byte[] digest = md.digest();
			return toHex(digest);
		}
		catch (final NoSuchAlgorithmException t) {
			throw new RuntimeException(t);
		}
	}

	/**
	 * Returns the hex representation of the byte array.
	 *
	 * @param bytes
	 * @return byte array as hex string
	 */
	public static String toHex(byte[] bytes) {
		final StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (int i = 0; i < bytes.length; i++) {
			if ((bytes[i] & 0xff) < 0x10) {
				sb.append('0');
			}
			sb.append(Long.toString(bytes[i] & 0xff, 16));
		}
		return sb.toString();
	}

	/**
	 * Returns the root path of the specified path. Returns a blank string if
	 * there is no root path.
	 *
	 * @param path
	 * @return root path or blank
	 */
	public static String getRootPath(String path) {
		if (path.indexOf('/') > -1) {
			return path.substring(0, path.lastIndexOf('/'));
		}
		return "";
	}

	/**
	 * Returns the path remainder after subtracting the basePath from the
	 * fullPath.
	 *
	 * @param basePath
	 * @param fullPath
	 * @return the relative path
	 */
	public static String getRelativePath(String basePath, String fullPath) {
		final String bp = basePath.replace('\\', '/').toLowerCase();
		final String fp = fullPath.replace('\\', '/').toLowerCase();
		if (fp.startsWith(bp)) {
			String relativePath = fullPath.substring(basePath.length()).replace('\\', '/');
			if ((relativePath.length() > 0) && (relativePath.charAt(0) == '/')) {
				relativePath = relativePath.substring(1);
			}
			return relativePath;
		}
		return fullPath;
	}

	/**
	 * Splits the space-separated string into a list of strings.
	 *
	 * @param value
	 * @return list of strings
	 */
	public static List<String> getStringsFromValue(String value) {
		return getStringsFromValue(value, " ");
	}

	/**
	 * Splits the string into a list of string by the specified separator.
	 *
	 * @param value
	 * @param separator
	 * @return list of strings
	 */
	public static List<String> getStringsFromValue(String value, String separator) {
		final List<String> strings = new ArrayList<String>();
		try {
			final String[] chunks = value.split(separator + "(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
			for (String chunk : chunks) {
				chunk = chunk.trim();
				if (chunk.length() > 0) {
					if ((chunk.charAt(0) == '"') && (chunk.charAt(chunk.length() - 1) == '"')) {
						// strip double quotes
						chunk = chunk.substring(1, chunk.length() - 1).trim();
					}
					strings.add(chunk);
				}
			}
		}
		catch (final PatternSyntaxException e) {
			throw new RuntimeException(e);
		}
		return strings;
	}

	/**
	 * Validates that a name is composed of letters, digits, or limited other
	 * characters.
	 *
	 * @param name
	 * @return the first invalid character found or null if string is acceptable
	 */
	public static Character findInvalidCharacter(String name) {
		final char[] validChars = { '/', '.', '_', '-', '~', '+' };
		for (final char c : name.toCharArray()) {
			if (!Character.isLetterOrDigit(c)) {
				boolean ok = false;
				for (final char vc : validChars) {
					ok |= c == vc;
				}
				if (!ok) {
					return c;
				}
			}
		}
		return null;
	}

	/**
	 * Simple fuzzy string comparison. This is a case-insensitive check. A
	 * single wildcard * value is supported.
	 *
	 * @param value
	 * @param pattern
	 * @return true if the value matches the pattern
	 */
	public static boolean fuzzyMatch(String value, String pattern) {
		if (value.equalsIgnoreCase(pattern)) {
			return true;
		}
		if (pattern.contains("*")) {
			boolean prefixMatches = false;
			boolean suffixMatches = false;

			final int wildcard = pattern.indexOf('*');
			final String prefix = pattern.substring(0, wildcard).toLowerCase();
			prefixMatches = value.toLowerCase().startsWith(prefix);

			if (pattern.length() > (wildcard + 1)) {
				final String suffix = pattern.substring(wildcard + 1).toLowerCase();
				suffixMatches = value.toLowerCase().endsWith(suffix);
				return prefixMatches && suffixMatches;
			}
			return prefixMatches || suffixMatches;
		}
		return false;
	}

	/**
	 * Compare two repository names for proper group sorting.
	 *
	 * @param r1
	 * @param r2
	 * @return
	 */
	public static int compareRepositoryNames(String r1, String r2) {
		// sort root repositories first, alphabetically
		// then sort grouped repositories, alphabetically
		r1 = r1.toLowerCase();
		r2 = r2.toLowerCase();
		final int s1 = r1.indexOf('/');
		final int s2 = r2.indexOf('/');
		if ((s1 == -1) && (s2 == -1)) {
			// neither grouped
			return r1.compareTo(r2);
		} else if ((s1 > -1) && (s2 > -1)) {
			// both grouped
			return r1.compareTo(r2);
		} else if (s1 == -1) {
			return -1;
		} else if (s2 == -1) {
			return 1;
		}
		return 0;
	}

	/**
	 * Sort grouped repository names.
	 *
	 * @param list
	 */
	public static void sortRepositorynames(List<String> list) {
		Collections.sort(list, new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				return compareRepositoryNames(o1, o2);
			}
		});
	}

	public static String getColor(String value) {
		int cs = 0;
		for (final char c : getMD5(value.toLowerCase()).toCharArray()) {
			cs += c;
		}
		final int n = (cs % 360);
		final float hue = ((float) n) / 360;
		return hsvToRgb(hue, 0.90f, 0.65f);
	}

	public static String hsvToRgb(float hue, float saturation, float value) {
		final int h = (int) (hue * 6);
		final float f = (hue * 6) - h;
		final float p = value * (1 - saturation);
		final float q = value * (1 - (f * saturation));
		final float t = value * (1 - ((1 - f) * saturation));

		switch (h) {
		case 0:
			return rgbToString(value, t, p);
		case 1:
			return rgbToString(q, value, p);
		case 2:
			return rgbToString(p, value, t);
		case 3:
			return rgbToString(p, q, value);
		case 4:
			return rgbToString(t, p, value);
		case 5:
			return rgbToString(value, p, q);
		default:
			throw new RuntimeException(
					"Something went wrong when converting from HSV to RGB. Input was " + hue + ", "
							+ saturation + ", " + value);
		}
	}

	public static String rgbToString(float r, float g, float b) {
		final String rs = Integer.toHexString((int) (r * 256));
		final String gs = Integer.toHexString((int) (g * 256));
		final String bs = Integer.toHexString((int) (b * 256));
		return "#" + rs + gs + bs;
	}

	/**
	 * Strips a trailing ".git" from the value.
	 *
	 * @param value
	 * @return a stripped value or the original value if .git is not found
	 */
	public static String stripDotGit(String value) {
		if (value.toLowerCase().endsWith(".git")) {
			return value.substring(0, value.length() - 4);
		}
		return value;
	}

	/**
	 * Count the number of lines in a string.
	 *
	 * @param value
	 * @return the line count
	 */
	public static int countLines(String value) {
		if (isEmpty(value)) {
			return 0;
		}
		return value.split("\n").length;
	}

	/**
	 * Returns the file extension of a path.
	 *
	 * @param path
	 * @return a blank string or a file extension
	 */
	public static String getFileExtension(String path) {
		final int lastDot = path.lastIndexOf('.');
		if (lastDot > -1) {
			return path.substring(lastDot + 1);
		}
		return "";
	}

	/**
	 * Returns the file extension of a path.
	 *
	 * @param path
	 * @return a blank string or a file extension
	 */
	public static String stripFileExtension(String path) {
		final int lastDot = path.lastIndexOf('.');
		if (lastDot > -1) {
			return path.substring(0, lastDot);
		}
		return path;
	}

	/**
	 * Replace all occurences of a substring within a string with another
	 * string.
	 *
	 * From Spring StringUtils.
	 *
	 * @param inString
	 *            String to examine
	 * @param oldPattern
	 *            String to replace
	 * @param newPattern
	 *            String to insert
	 * @return a String with the replacements
	 */
	public static String replace(String inString, String oldPattern, String newPattern) {
		final StringBuilder sb = new StringBuilder();
		int pos = 0; // our position in the old string
		int index = inString.indexOf(oldPattern);
		// the index of an occurrence we've found, or -1
		final int patLen = oldPattern.length();
		while (index >= 0) {
			sb.append(inString.substring(pos, index));
			sb.append(newPattern);
			pos = index + patLen;
			index = inString.indexOf(oldPattern, pos);
		}
		sb.append(inString.substring(pos));
		// remember to append any characters to the right of a match
		return sb.toString();
	}

	/**
	 * Decodes a string by trying several charsets until one does not throw a
	 * coding exception. Last resort is to interpret as UTF-8 with illegal
	 * character substitution.
	 *
	 * @param content
	 * @param charsets
	 *            optional
	 * @return a string
	 */
	public static String decodeString(byte[] content, String... charsets) {
		final Set<String> sets = new LinkedHashSet<String>();
		if (!ArrayUtils.isEmpty(charsets)) {
			sets.addAll(Arrays.asList(charsets));
		}
		String value = null;
		sets.addAll(Arrays.asList("UTF-8", "ISO-8859-1", Charset.defaultCharset().name()));
		for (final String charset : sets) {
			try {
				final Charset cs = Charset.forName(charset);
				final CharsetDecoder decoder = cs.newDecoder();
				final CharBuffer buffer = decoder.decode(ByteBuffer.wrap(content));
				value = buffer.toString();
				break;
			}
			catch (final CharacterCodingException e) {
				// ignore and advance to the next charset
			}
			catch (final IllegalCharsetNameException e) {
				// ignore illegal charset names
			}
			catch (final UnsupportedCharsetException e) {
				// ignore unsupported charsets
			}
		}
		if ((value != null) && value.startsWith("\uFEFF")) {
			// strip UTF-8 BOM
			return value.substring(1);
		}
		return value;
	}

	/**
	 * Attempt to extract a repository name from a given url using regular
	 * expressions. If no match is made, then return whatever trails after the
	 * final / character.
	 *
	 * @param regexUrls
	 * @return a repository path
	 */
	public static String extractRepositoryPath(String url, String... urlpatterns) {
		for (final String urlPattern : urlpatterns) {
			final Pattern p = Pattern.compile(urlPattern);
			final Matcher m = p.matcher(url);
			while (m.find()) {
				final String repositoryPath = m.group(1);
				return repositoryPath;
			}
		}
		// last resort
		if (url.lastIndexOf('/') > -1) {
			return url.substring(url.lastIndexOf('/') + 1);
		}
		return url;
	}

	/**
	 * Converts a string with \nnn sequences into a UTF-8 encoded string.
	 * 
	 * @param input
	 * @return
	 */
	public static String convertOctal(String input) {
		try {
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			final Pattern p = Pattern.compile("(\\\\\\d{3})");
			final Matcher m = p.matcher(input);
			int i = 0;
			while (m.find()) {
				bytes.write(input.substring(i, m.start()).getBytes("UTF-8"));
				// replace octal encoded value
				// strip leading \ character
				final String oct = m.group().substring(1);
				bytes.write(Integer.parseInt(oct, 8));
				i = m.end();
			}
			if (bytes.size() == 0) {
				// no octal matches
				return input;
			} else {
				if (i < input.length()) {
					// add remainder of string
					bytes.write(input.substring(i).getBytes("UTF-8"));
				}
			}
			return bytes.toString("UTF-8");
		}
		catch (final Exception e) {
			e.printStackTrace();
		}
		return input;
	}

	/**
	 * Returns the first path element of a path string. If no path separator is
	 * found in the path, an empty string is returned.
	 *
	 * @param path
	 * @return the first element in the path
	 */
	public static String getFirstPathElement(String path) {
		if (path.indexOf('/') > -1) {
			return path.substring(0, path.indexOf('/')).trim();
		}
		return "";
	}

	/**
	 * Returns the last path element of a path string
	 *
	 * @param path
	 * @return the last element in the path
	 */
	public static String getLastPathElement(String path) {
		if (path.indexOf('/') > -1) {
			return path.substring(path.lastIndexOf('/') + 1);
		}
		return path;
	}

	/**
	 * Variation of String.matches() which disregards case issues.
	 *
	 * @param regex
	 * @param input
	 * @return true if the pattern matches
	 */
	public static boolean matchesIgnoreCase(String input, String regex) {
		final Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		final Matcher m = p.matcher(input);
		return m.matches();
	}

	/**
	 * Removes new line and carriage return chars from a string. If input value
	 * is null an empty string is returned.
	 *
	 * @param input
	 * @return a sanitized or empty string
	 */
	public static String removeNewlines(String input) {
		if (input == null) {
			return "";
		}
		return input.replace('\n', ' ').replace('\r', ' ').trim();
	}

	/**
	 * Encode the username for user in an url.
	 *
	 * @param name
	 * @return the encoded name
	 */
	public static String encodeUsername(String name) {
		return name.replace("@", "%40").replace(" ", "%20").replace("\\", "%5C");
	}

	/**
	 * Decode a username from an encoded url.
	 *
	 * @param name
	 * @return the decoded name
	 */
	public static String decodeUsername(String name) {
		return name.replace("%40", "@").replace("%20", " ").replace("%5C", "\\");
	}
}