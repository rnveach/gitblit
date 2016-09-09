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
package com.gitblit.authority;

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.eclipse.jgit.lib.Config;

import com.gitblit.Constants;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.utils.X509Utils.RevocationReason;

public class UserCertificateModel implements Comparable<UserCertificateModel> {
	public UserModel user;
	public Date expires;
	public List<X509Certificate> certs;
	public List<String> revoked;
	public String notes;

	public UserCertificateModel(UserModel user) {
		this.user = user;
	}

	public void update(Config config) {
		if (this.expires == null) {
			config.unset("user", this.user.username, "expires");
		} else {
			final SimpleDateFormat df = new SimpleDateFormat(Constants.ISO8601);
			config.setString("user", this.user.username, "expires", df.format(this.expires));
		}
		if (StringUtils.isEmpty(this.notes)) {
			config.unset("user", this.user.username, "notes");
		} else {
			config.setString("user", this.user.username, "notes", this.notes);
		}
		if (ArrayUtils.isEmpty(this.revoked)) {
			config.unset("user", this.user.username, "revoked");
		} else {
			config.setStringList("user", this.user.username, "revoked", this.revoked);
		}
	}

	@Override
	public int compareTo(UserCertificateModel o) {
		return this.user.compareTo(o.user);
	}

	public void revoke(BigInteger serial, RevocationReason reason) {
		if (this.revoked == null) {
			this.revoked = new ArrayList<String>();
		}
		this.revoked.add(serial.toString() + ":" + reason.ordinal());
		this.expires = null;
		for (final X509Certificate cert : this.certs) {
			if (!isRevoked(cert.getSerialNumber())) {
				if (!isExpired(cert.getNotAfter())) {
					if ((this.expires == null) || cert.getNotAfter().after(this.expires)) {
						this.expires = cert.getNotAfter();
					}
				}
			}
		}
	}

	public boolean isRevoked(BigInteger serial) {
		return isRevoked(serial.toString());
	}

	public boolean isRevoked(String serial) {
		if (ArrayUtils.isEmpty(this.revoked)) {
			return false;
		}
		final String sn = serial + ":";
		for (final String s : this.revoked) {
			if (s.startsWith(sn)) {
				return true;
			}
		}
		return false;
	}

	public RevocationReason getRevocationReason(BigInteger serial) {
		try {
			final String sn = serial + ":";
			for (final String s : this.revoked) {
				if (s.startsWith(sn)) {
					final String r = s.substring(sn.length());
					final int i = Integer.parseInt(r);
					return RevocationReason.values()[i];
				}
			}
		}
		catch (final Exception e) {
		}
		return RevocationReason.unspecified;
	}

	public CertificateStatus getStatus() {
		if (this.expires == null) {
			return CertificateStatus.unknown;
		} else if (isExpired(this.expires)) {
			return CertificateStatus.expired;
		} else if (isExpiring(this.expires)) {
			return CertificateStatus.expiring;
		}
		return CertificateStatus.ok;
	}

	public boolean hasExpired() {
		return (this.expires != null) && isExpiring(this.expires);
	}

	public CertificateStatus getStatus(X509Certificate cert) {
		if (isRevoked(cert.getSerialNumber())) {
			return CertificateStatus.revoked;
		} else if (isExpired(cert.getNotAfter())) {
			return CertificateStatus.expired;
		} else if (isExpiring(cert.getNotAfter())) {
			return CertificateStatus.expiring;
		}
		return CertificateStatus.ok;
	}

	private static boolean isExpiring(Date date) {
		return (date.getTime() - System.currentTimeMillis()) <= (TimeUtils.ONEDAY * 30);
	}

	private static boolean isExpired(Date date) {
		return date.getTime() < System.currentTimeMillis();
	}
}