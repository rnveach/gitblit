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
package com.gitblit.wicket.pages;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.CompoundPropertyModel;

import com.gitblit.Constants.FederationProposalResult;
import com.gitblit.models.FederationProposal;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.FederationUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.RequiresAdminRole;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.RepositoriesPanel;

@RequiresAdminRole
public class SendProposalPage extends RootSubPage {

	public String myUrl;

	public String destinationUrl;

	public String message;

	public SendProposalPage(PageParameters params) {
		super(params);

		setupPage(getString("gb.sendProposal"), "");
		setStatelessHint(true);

		final String token = WicketUtils.getToken(params);

		this.myUrl = WicketUtils.getGitblitURL(getRequest());
		this.destinationUrl = "https://";

		// temporary proposal
		final FederationProposal proposal = app().federation().createFederationProposal(this.myUrl,
				token);
		if (proposal == null) {
			error(getString("gb.couldNotCreateFederationProposal"), true);
		}

		final CompoundPropertyModel<SendProposalPage> model = new CompoundPropertyModel<SendProposalPage>(
				this);

		final Form<SendProposalPage> form = new Form<SendProposalPage>("editForm", model) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit() {
				// confirm a repository name was entered
				if (StringUtils.isEmpty(SendProposalPage.this.myUrl)) {
					error(getString("gb.pleaseSetGitblitUrl"));
					return;
				}
				if (StringUtils.isEmpty(SendProposalPage.this.destinationUrl)) {
					error(getString("gb.pleaseSetDestinationUrl"));
					return;
				}

				// build new proposal
				final FederationProposal proposal = app().federation().createFederationProposal(
						SendProposalPage.this.myUrl, token);
				proposal.url = SendProposalPage.this.myUrl;
				proposal.message = SendProposalPage.this.message;
				try {
					final FederationProposalResult res = FederationUtils.propose(
							SendProposalPage.this.destinationUrl, proposal);
					switch (res) {
					case ACCEPTED:
						info(MessageFormat.format(getString("gb.proposalReceived"),
								SendProposalPage.this.destinationUrl));
						setResponsePage(RepositoriesPage.class);
						break;
					case NO_POKE:
						error(MessageFormat.format(getString("noGitblitFound"),
								SendProposalPage.this.destinationUrl, SendProposalPage.this.myUrl));
						break;
					case NO_PROPOSALS:
						error(MessageFormat.format(getString("gb.noProposals"),
								SendProposalPage.this.destinationUrl));
						break;
					case FEDERATION_DISABLED:
						error(MessageFormat.format(getString("gb.noFederation"),
								SendProposalPage.this.destinationUrl));
						break;
					case MISSING_DATA:
						error(MessageFormat.format(getString("gb.proposalFailed"),
								SendProposalPage.this.destinationUrl));
						break;
					case ERROR:
						error(MessageFormat.format(getString("gb.proposalError"),
								SendProposalPage.this.destinationUrl));
						break;
					}
				}
				catch (final Exception e) {
					if (!StringUtils.isEmpty(e.getMessage())) {
						error(e.getMessage());
					} else {
						error(getString("gb.failedToSendProposal"));
					}
				}
			}
		};
		form.add(new TextField<String>("myUrl"));
		form.add(new TextField<String>("destinationUrl"));
		form.add(new TextField<String>("message"));
		form.add(new Label("tokenType", proposal.tokenType.name()));
		form.add(new Label("token", proposal.token));

		form.add(new Button("save"));
		final Button cancel = new Button("cancel") {
			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
				setResponsePage(FederationPage.class);
			}
		};
		cancel.setDefaultFormProcessing(false);
		form.add(cancel);
		add(form);

		final List<RepositoryModel> repositories = new ArrayList<RepositoryModel>(
				proposal.repositories.values());
		final RepositoriesPanel repositoriesPanel = new RepositoriesPanel("repositoriesPanel",
				false, false, repositories, false, getAccessRestrictions());
		add(repositoriesPanel);
	}

	@Override
	protected Class<? extends BasePage> getRootNavPageClass() {
		return FederationPage.class;
	}
}
