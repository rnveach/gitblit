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
import com.gitblit.utils.X509Utils.X509Metadata;

public class DefaultOidsPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final JTextField organizationalUnit;
	private final JTextField organization;
	private final JTextField locality;
	private final JTextField stateProvince;
	private final JTextField countryCode;

	public DefaultOidsPanel(X509Metadata metadata) {
		super();

		this.organizationalUnit = new JTextField(metadata.getOID("OU", ""), 20);
		this.organization = new JTextField(metadata.getOID("O", ""), 20);
		this.locality = new JTextField(metadata.getOID("L", ""), 20);
		this.stateProvince = new JTextField(metadata.getOID("ST", ""), 20);
		this.countryCode = new JTextField(metadata.getOID("C", ""), 20);

		setLayout(new GridLayout(0, 1, Utils.MARGIN, Utils.MARGIN));
		add(Utils.newFieldPanel(Translation.get("gb.organizationalUnit") + " (OU)",
				this.organizationalUnit));
		add(Utils.newFieldPanel(Translation.get("gb.organization") + " (O)", this.organization));
		add(Utils.newFieldPanel(Translation.get("gb.locality") + " (L)", this.locality));
		add(Utils.newFieldPanel(Translation.get("gb.stateProvince") + " (ST)", this.stateProvince));
		add(Utils.newFieldPanel(Translation.get("gb.countryCode") + " (C)", this.countryCode));
	}

	public void update(X509Metadata metadata) {
		metadata.setOID("OU", this.organizationalUnit.getText());
		metadata.setOID("O", this.organization.getText());
		metadata.setOID("L", this.locality.getText());
		metadata.setOID("ST", this.stateProvince.getText());
		metadata.setOID("C", this.countryCode.getText());
	}

	public String getOrganizationalUnit() {
		return this.organizationalUnit.getText();
	}

	public String getOrganization() {
		return this.organization.getText();
	}

	public String getLocality() {
		return this.locality.getText();
	}

	public String getStateProvince() {
		return this.stateProvince.getText();
	}

	public String getCountryCode() {
		return this.countryCode.getText();
	}
}
