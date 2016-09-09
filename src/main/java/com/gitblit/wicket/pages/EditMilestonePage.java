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
package com.gitblit.wicket.pages;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.TicketMilestone;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.Html5DateField;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.BasePanel.JavascriptEventConfirmation;

/**
 * Page for creating a new milestone.
 *
 * @author James Moger
 *
 */
public class EditMilestonePage extends RepositoryPage {

	private final String oldName;

	private IModel<String> nameModel;

	private IModel<Date> dueModel;

	private IModel<Status> statusModel;

	private IModel<Boolean> notificationModel;

	public EditMilestonePage(PageParameters params) {
		super(params);

		final RepositoryModel model = getRepositoryModel();
		if (!app().tickets().isAcceptingTicketUpdates(model)) {
			// ticket service is read-only
			throw new RestartResponseException(TicketsPage.class,
					WicketUtils.newOpenTicketsParameter(this.repositoryName));
		}

		UserModel currentUser = GitBlitWebSession.get().getUser();
		if (currentUser == null) {
			currentUser = UserModel.ANONYMOUS;
		}

		if (!currentUser.isAuthenticated || !currentUser.canAdmin(model)) {
			// administration prohibited
			throw new RestartResponseException(TicketsPage.class,
					WicketUtils.newOpenTicketsParameter(this.repositoryName));
		}

		this.oldName = WicketUtils.getObject(params);
		if (StringUtils.isEmpty(this.oldName)) {
			// milestone not specified
			throw new RestartResponseException(TicketsPage.class,
					WicketUtils.newOpenTicketsParameter(this.repositoryName));
		}

		final TicketMilestone tm = app().tickets().getMilestone(getRepositoryModel(), this.oldName);
		if (tm == null) {
			// milestone does not exist
			throw new RestartResponseException(TicketsPage.class,
					WicketUtils.newOpenTicketsParameter(this.repositoryName));
		}

		setStatelessHint(false);
		setOutputMarkupId(true);

		final Form<Void> form = new Form<Void>("editForm");
		add(form);

		this.nameModel = Model.of(tm.name);
		this.dueModel = Model.of(tm.due);
		this.statusModel = Model.of(tm.status);
		this.notificationModel = Model.of(true);

		form.add(new TextField<String>("name", this.nameModel));
		form.add(new Html5DateField("due", this.dueModel, "yyyy-MM-dd"));
		form.add(new Label("dueFormat", "yyyy-MM-dd"));
		form.add(new CheckBox("notify", this.notificationModel));
		addBottomScriptInline("{var e=document.createElement('input');e.type='date';if(e.type=='date'){$('[name=\"due\"]~.help-inline').hide()}}");
		addBottomScript("scripts/wicketHtml5Patch.js");

		final List<Status> statusChoices = Arrays.asList(Status.Open, Status.Closed);
		form.add(new DropDownChoice<TicketModel.Status>("status", this.statusModel, statusChoices));

		form.add(new AjaxButton("save") {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
				final String name = EditMilestonePage.this.nameModel.getObject();
				if (StringUtils.isEmpty(name)) {
					return;
				}

				final Date due = EditMilestonePage.this.dueModel.getObject();
				final Status status = EditMilestonePage.this.statusModel.getObject();
				final boolean rename = !name.equals(EditMilestonePage.this.oldName);
				final boolean notify = EditMilestonePage.this.notificationModel.getObject();

				final UserModel currentUser = GitBlitWebSession.get().getUser();
				final String createdBy = currentUser.username;

				final TicketMilestone tm = app().tickets().getMilestone(getRepositoryModel(),
						EditMilestonePage.this.oldName);
				tm.setName(name);
				tm.setDue(due);
				tm.status = status;

				boolean success = true;
				if (rename) {
					success = app().tickets().renameMilestone(getRepositoryModel(),
							EditMilestonePage.this.oldName, name, createdBy, notify);
				}

				if (success && app().tickets().updateMilestone(getRepositoryModel(), tm, createdBy)) {
					redirectTo(TicketsPage.class, WicketUtils
							.newOpenTicketsParameter(EditMilestonePage.this.repositoryName));
				} else {
					// TODO error
				}
			}
		});
		final Button cancel = new Button("cancel") {
			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
				setResponsePage(TicketsPage.class,
						WicketUtils.newOpenTicketsParameter(EditMilestonePage.this.repositoryName));
			}
		};
		cancel.setDefaultFormProcessing(false);
		form.add(cancel);

		final Link<Void> delete = new Link<Void>("delete") {

			private static final long serialVersionUID = 1L;

			@Override
			public void onClick() {
				final UserModel currentUser = GitBlitWebSession.get().getUser();
				final String createdBy = currentUser.username;
				final boolean notify = EditMilestonePage.this.notificationModel.getObject();

				if (app().tickets().deleteMilestone(getRepositoryModel(),
						EditMilestonePage.this.oldName, createdBy, notify)) {
					setResponsePage(TicketsPage.class,
							WicketUtils
									.newOpenTicketsParameter(EditMilestonePage.this.repositoryName));
				} else {
					error(MessageFormat.format(getString("gb.milestoneDeleteFailed"),
							EditMilestonePage.this.oldName));
				}
			}
		};

		delete.add(new JavascriptEventConfirmation("onclick", MessageFormat.format(
				getString("gb.deleteMilestone"), this.oldName)));

		form.add(delete);
	}

	@Override
	protected String getPageName() {
		return getString("gb.editMilestone");
	}

	@Override
	protected Class<? extends BasePage> getRepoNavPageClass() {
		return TicketsPage.class;
	}
}
