/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.gitblit.transport.ssh;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.sshd.common.keyprovider.AbstractKeyPairProvider;
import org.apache.sshd.common.util.SecurityUtils;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.PasswordFinder;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;

/**
 * This host key provider loads private keys from the specified files.
 *
 * Note that this class has a direct dependency on BouncyCastle and won't work
 * unless it has been correctly registered as a security provider.
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class FileKeyPairProvider extends AbstractKeyPairProvider {

	private String[] files;
	private PasswordFinder passwordFinder;

	public FileKeyPairProvider() {
	}

	public FileKeyPairProvider(String[] files) {
		this.files = files;
	}

	public FileKeyPairProvider(String[] files, PasswordFinder passwordFinder) {
		this.files = files;
		this.passwordFinder = passwordFinder;
	}

	public String[] getFiles() {
		return this.files;
	}

	public void setFiles(String[] files) {
		this.files = files;
	}

	public PasswordFinder getPasswordFinder() {
		return this.passwordFinder;
	}

	public void setPasswordFinder(PasswordFinder passwordFinder) {
		this.passwordFinder = passwordFinder;
	}

	@Override
	public Iterable<KeyPair> loadKeys() {
		if (!SecurityUtils.isBouncyCastleRegistered()) {
			throw new IllegalStateException("BouncyCastle must be registered as a JCE provider");
		}
		return new Iterable<KeyPair>() {
			@Override
			public Iterator<KeyPair> iterator() {
				return new Iterator<KeyPair>() {
					private final Iterator<String> iterator = Arrays.asList(
							FileKeyPairProvider.this.files).iterator();
					private KeyPair nextKeyPair;
					private boolean nextKeyPairSet = false;

					@Override
					public boolean hasNext() {
						return this.nextKeyPairSet || setNextObject();
					}

					@Override
					public KeyPair next() {
						if (!this.nextKeyPairSet) {
							if (!setNextObject()) {
								throw new NoSuchElementException();
							}
						}
						this.nextKeyPairSet = false;
						return this.nextKeyPair;
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}

					private boolean setNextObject() {
						while (this.iterator.hasNext()) {
							final String file = this.iterator.next();
							this.nextKeyPair = doLoadKey(file);
							if (this.nextKeyPair != null) {
								this.nextKeyPairSet = true;
								return true;
							}
						}
						return false;
					}

				};
			}
		};
	}

	protected KeyPair doLoadKey(String file) {
		try {
			final PEMParser r = new PEMParser(new InputStreamReader(new FileInputStream(file)));
			try {
				Object o = r.readObject();

				final JcaPEMKeyConverter pemConverter = new JcaPEMKeyConverter();
				pemConverter.setProvider("BC");
				if ((this.passwordFinder != null) && (o instanceof PEMEncryptedKeyPair)) {
					final JcePEMDecryptorProviderBuilder decryptorBuilder = new JcePEMDecryptorProviderBuilder();
					final PEMDecryptorProvider pemDecryptor = decryptorBuilder
							.build(this.passwordFinder.getPassword());
					o = pemConverter.getKeyPair(((PEMEncryptedKeyPair) o)
							.decryptKeyPair(pemDecryptor));
				}

				if (o instanceof PEMKeyPair) {
					o = pemConverter.getKeyPair((PEMKeyPair) o);
					return (KeyPair) o;
				} else if (o instanceof KeyPair) {
					return (KeyPair) o;
				}
			}
			finally {
				r.close();
			}
		}
		catch (final Exception e) {
			this.log.warn("Unable to read key " + file, e);
		}
		return null;
	}

}
