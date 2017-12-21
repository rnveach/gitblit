package com.gitblit.tests;

import org.apache.commons.codec.digest.DigestUtils;

/*
 * Test helper structure to create blobs of a given size
 */
public final class BlobInfo {
	public byte[] blob;
	public String hash;
	public int length;
	
	public BlobInfo(int nBytes) {
		blob = new byte[nBytes];
		new java.util.Random().nextBytes(blob);
		hash = DigestUtils.sha256Hex(blob);
		length = nBytes;
	}
}
