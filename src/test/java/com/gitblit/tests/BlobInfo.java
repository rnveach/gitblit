package com.gitblit.tests;

import org.apache.commons.codec.digest.DigestUtils;

/*
 * Test helper structure to create blobs of a given size
 */
public final class BlobInfo {
	public final byte[] blob;
	public final String hash;
	public final int length;
	
	public BlobInfo(int nBytes) {
		blob = new byte[nBytes];
		new java.util.Random().nextBytes(blob);
		hash = DigestUtils.sha256Hex(blob);
		length = nBytes;
	}
}
