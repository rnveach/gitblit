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

import java.awt.GridLayout;

import javax.swing.JPanel;
import javax.swing.JTextField;

import com.gitblit.client.Translation;

public class UserOidsPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final JTextField displayname;
	private final JTextField username;
	private final JTextField emailAddress;
	private final JTextField organizationalUnit;
	private final JTextField organization;
	private final JTextField locality;
	private final JTextField stateProvince;
	private final JTextField countryCode;

	public UserOidsPanel() {
		super();

		this.displayname = new JTextField(20);
		this.username = new JTextField(20);
		this.username.setEditable(false);
		this.emailAddress = new JTextField(20);
		this.organizationalUnit = new JTextField(20);
		this.organization = new JTextField(20);
		this.locality = new JTextField(20);
		this.stateProvince = new JTextField(20);
		this.countryCode = new JTextField(20);

		setLayout(new GridLayout(0, 1, Utils.MARGIN, Utils.MARGIN));
		add(Utils.newFieldPanel(Translation.get("gb.displayName"), this.displayname));
		add(Utils.newFieldPanel(Translation.get("gb.username") + " (CN)", this.username));
		add(Utils.newFieldPanel(Translation.get("gb.emailAddress") + " (E)", this.emailAddress));
		add(Utils.newFieldPanel(Translation.get("gb.organizationalUnit") + " (OU)",
				this.organizationalUnit));
		add(Utils.newFieldPanel(Translation.get("gb.organization") + " (O)", this.organization));
		add(Utils.newFieldPanel(Translation.get("gb.locality") + " (L)", this.locality));
		add(Utils.newFieldPanel(Translation.get("gb.stateProvince") + " (ST)", this.stateProvince));
		add(Utils.newFieldPanel(Translation.get("gb.countryCode") + " (C)", this.countryCode));
	}

	public void setUserCertificateModel(UserCertificateModel ucm) {
		setEditable(false);
		this.displayname.setText(ucm == null ? "" : ucm.user.getDisplayName());
		this.username.setText(ucm == null ? "" : ucm.user.username);
		this.emailAddress.setText(ucm == null ? "" : ucm.user.emailAddress);
		this.organizationalUnit.setText(ucm == null ? "" : ucm.user.organizationalUnit);
		this.organization.setText(ucm == null ? "" : ucm.user.organization);
		this.locality.setText(ucm == null ? "" : ucm.user.locality);
		this.stateProvince.setText(ucm == null ? "" : ucm.user.stateProvince);
		this.countryCode.setText(ucm == null ? "" : ucm.user.countryCode);
	}

	public void setEditable(boolean editable) {
		this.displayname.setEditable(editable);
		// username.setEditable(editable);
		this.emailAddress.setEditable(editable);
		this.organizationalUnit.setEditable(editable);
		this.organization.setEditable(editable);
		this.locality.setEditable(editable);
		this.stateProvince.setEditable(editable);
		this.countryCode.setEditable(editable);
	}

	protected void updateUser(UserCertificateModel ucm) {
		ucm.user.displayName = this.displayname.getText();
		ucm.user.username = this.username.getText();
		ucm.user.emailAddress = this.emailAddress.getText();
		ucm.user.organizationalUnit = this.organizationalUnit.getText();
		ucm.user.organization = this.organization.getText();
		ucm.user.locality = this.locality.getText();
		ucm.user.stateProvince = this.stateProvince.getText();
		ucm.user.countryCode = this.countryCode.getText();
	}
}
