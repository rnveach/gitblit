/*
 * Copyright 2014 gitblit.com.
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
package com.gitblit.wicket.panels;

import java.io.Serializable;
import java.text.MessageFormat;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.target.basic.RedirectRequestTarget;

import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.SessionlessForm;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.BasePage;

public class TicketSearchForm extends SessionlessForm<Void> implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String repositoryName;

	private final IModel<String> searchBoxModel;

	public TicketSearchForm(String id, String repositoryName, String text,
			Class<? extends BasePage> pageClass, PageParameters params) {

		super(id, pageClass, params);

		this.repositoryName = repositoryName;
		this.searchBoxModel = new Model<String>(text == null ? "" : text);

		final TextField<String> searchBox = new TextField<String>("ticketSearchBox",
				this.searchBoxModel);
		add(searchBox);
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();
		WicketUtils.setHtmlTooltip(get("ticketSearchBox"),
				MessageFormat.format(getString("gb.searchTicketsTooltip"), ""));
		WicketUtils.setInputPlaceholder(get("ticketSearchBox"), getString("gb.searchTickets"));
	}

	@Override
	public void onSubmit() {
		final String searchString = this.searchBoxModel.getObject();
		if (StringUtils.isEmpty(searchString)) {
			// redirect to self to avoid wicket page update bug
			final String absoluteUrl = getAbsoluteUrl();
			getRequestCycle().setRequestTarget(new RedirectRequestTarget(absoluteUrl));
			return;
		}

		// use an absolute url to workaround Wicket-Tomcat problems with
		// mounted url parameters (issue-111)
		final PageParameters params = WicketUtils.newRepositoryParameter(this.repositoryName);
		params.add("s", searchString);
		final String absoluteUrl = getAbsoluteUrl(this.pageClass, params);
		getRequestCycle().setRequestTarget(new RedirectRequestTarget(absoluteUrl));
	}
}
