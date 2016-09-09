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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.wicket.PageParameters;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.Field;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.TicketModel.Type;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.TicketMilestone;
import com.gitblit.tickets.TicketNotifier;
import com.gitblit.tickets.TicketResponsible;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.MarkdownTextArea;
import com.google.common.base.Optional;

/**
 * Page for editing a ticket.
 *
 * @author James Moger
 *
 */
public class EditTicketPage extends RepositoryPage {

	static final String NIL = "<nil>";

	static final String ESC_NIL = StringUtils.escapeForHtml(NIL, false);

	private IModel<TicketModel.Type> typeModel;

	private IModel<String> titleModel;

	private MarkdownTextArea descriptionEditor;

	private IModel<String> topicModel;

	private IModel<String> mergeToModel;

	private IModel<Status> statusModel;

	private IModel<TicketResponsible> responsibleModel;

	private IModel<TicketMilestone> milestoneModel;

	private Label descriptionPreview;

	private IModel<TicketModel.Priority> priorityModel;

	private IModel<TicketModel.Severity> severityModel;

	public EditTicketPage(PageParameters params) {
		super(params);

		UserModel currentUser = GitBlitWebSession.get().getUser();
		if (currentUser == null) {
			currentUser = UserModel.ANONYMOUS;
		}

		long ticketId = 0L;
		try {
			final String h = WicketUtils.getObject(params);
			ticketId = Long.parseLong(h);
		}
		catch (final Exception e) {
			setResponsePage(TicketsPage.class,
					WicketUtils.newRepositoryParameter(this.repositoryName));
		}

		TicketModel ticket = app().tickets().getTicket(getRepositoryModel(), ticketId);
		if ((ticket == null) || !currentUser.canEdit(ticket, getRepositoryModel())
				|| !app().tickets().isAcceptingTicketUpdates(getRepositoryModel())) {
			setResponsePage(TicketsPage.class,
					WicketUtils.newObjectParameter(this.repositoryName, "" + ticketId));

			// create a placeholder object so we don't trigger NPEs
			ticket = new TicketModel();
		}

		this.typeModel = Model.of(ticket.type);
		this.titleModel = Model.of(ticket.title);
		this.topicModel = Model.of(ticket.topic == null ? "" : ticket.topic);
		this.responsibleModel = Model.of();
		this.milestoneModel = Model.of();
		this.mergeToModel = Model.of(ticket.mergeTo == null ? getRepositoryModel().mergeTo
				: ticket.mergeTo);
		this.statusModel = Model.of(ticket.status);
		this.priorityModel = Model.of(ticket.priority);
		this.severityModel = Model.of(ticket.severity);

		setStatelessHint(false);
		setOutputMarkupId(true);

		final Form<Void> form = new Form<Void>("editForm");
		add(form);

		List<Type> typeChoices;
		if (ticket.isProposal()) {
			typeChoices = Arrays.asList(Type.Proposal);
		} else {
			typeChoices = Arrays.asList(TicketModel.Type.choices());
		}
		form.add(new DropDownChoice<TicketModel.Type>("type", this.typeModel, typeChoices));

		form.add(new TextField<String>("title", this.titleModel));
		form.add(new TextField<String>("topic", this.topicModel));

		final IModel<String> markdownPreviewModel = Model
				.of(ticket.body == null ? "" : ticket.body);
		this.descriptionPreview = new Label("descriptionPreview", markdownPreviewModel);
		this.descriptionPreview.setEscapeModelStrings(false);
		this.descriptionPreview.setOutputMarkupId(true);
		form.add(this.descriptionPreview);

		this.descriptionEditor = new MarkdownTextArea("description", markdownPreviewModel,
				this.descriptionPreview);
		this.descriptionEditor.setRepository(this.repositoryName);
		this.descriptionEditor.setText(ticket.body);
		form.add(this.descriptionEditor);

		// status
		List<Status> statusChoices;
		if (ticket.isClosed()) {
			statusChoices = Arrays.asList(ticket.status, Status.Open);
		} else if (ticket.isProposal()) {
			statusChoices = Arrays.asList(TicketModel.Status.proposalWorkflow);
		} else if (ticket.isBug()) {
			statusChoices = Arrays.asList(TicketModel.Status.bugWorkflow);
		} else {
			statusChoices = Arrays.asList(TicketModel.Status.requestWorkflow);
		}
		final Fragment status = new Fragment("status", "statusFragment", this);
		status.add(new DropDownChoice<TicketModel.Status>("status", this.statusModel, statusChoices));
		form.add(status);

		final List<TicketModel.Severity> severityChoices = Arrays.asList(TicketModel.Severity
				.choices());
		form.add(new DropDownChoice<TicketModel.Severity>("severity", this.severityModel,
				severityChoices));

		if (currentUser.canAdmin(ticket, getRepositoryModel())) {
			// responsible
			final Set<String> userlist = new TreeSet<String>(ticket.getParticipants());

			if (UserModel.ANONYMOUS.canPush(getRepositoryModel())
					|| (AuthorizationControl.AUTHENTICATED == getRepositoryModel().authorizationControl)) {
				// authorization is ANONYMOUS or AUTHENTICATED (i.e. all users
				// can be set responsible)
				userlist.addAll(app().users().getAllUsernames());
			} else {
				// authorization is by NAMED users (users with PUSH permission
				// can be set responsible)
				for (final RegistrantAccessPermission rp : app().repositories()
						.getUserAccessPermissions(getRepositoryModel())) {
					if (rp.permission.atLeast(AccessPermission.PUSH)) {
						userlist.add(rp.registrant);
					}
				}
			}

			final List<TicketResponsible> responsibles = new ArrayList<TicketResponsible>();
			for (final String username : userlist) {
				final UserModel user = app().users().getUserModel(username);
				if ((user != null) && !user.disabled) {
					final TicketResponsible responsible = new TicketResponsible(user);
					responsibles.add(responsible);
					if (user.username.equals(ticket.responsible)) {
						this.responsibleModel.setObject(responsible);
					}
				}
			}
			Collections.sort(responsibles);
			responsibles.add(new TicketResponsible(NIL, "", ""));
			final Fragment responsible = new Fragment("responsible", "responsibleFragment", this);
			responsible.add(new DropDownChoice<TicketResponsible>("responsible",
					this.responsibleModel, responsibles));
			form.add(responsible.setVisible(!responsibles.isEmpty()));

			// milestone
			final List<TicketMilestone> milestones = app().tickets().getMilestones(
					getRepositoryModel(), Status.Open);
			for (final TicketMilestone milestone : milestones) {
				if (milestone.name.equals(ticket.milestone)) {
					this.milestoneModel.setObject(milestone);
					break;
				}
			}
			if ((this.milestoneModel.getObject() == null) && !StringUtils.isEmpty(ticket.milestone)) {
				// ensure that this unrecognized milestone is listed
				// so that we get the <nil> selection.
				final TicketMilestone tms = new TicketMilestone(ticket.milestone);
				milestones.add(tms);
				this.milestoneModel.setObject(tms);
			}
			if (!milestones.isEmpty()) {
				milestones.add(new TicketMilestone(NIL));
			}

			// milestone
			final Fragment milestone = new Fragment("milestone", "milestoneFragment", this);
			milestone.add(new DropDownChoice<TicketMilestone>("milestone", this.milestoneModel,
					milestones));
			form.add(milestone.setVisible(!milestones.isEmpty()));

			// priority
			final Fragment priority = new Fragment("priority", "priorityFragment", this);
			final List<TicketModel.Priority> priorityChoices = Arrays.asList(TicketModel.Priority
					.choices());
			priority.add(new DropDownChoice<TicketModel.Priority>("priority", this.priorityModel,
					priorityChoices));
			form.add(priority);

			// mergeTo (integration branch)
			final List<String> branches = new ArrayList<String>();
			for (final String branch : getRepositoryModel().getLocalBranches()) {
				// exclude ticket branches
				if (!branch.startsWith(Constants.R_TICKET)) {
					branches.add(Repository.shortenRefName(branch));
				}
			}
			branches.remove(Repository.shortenRefName(getRepositoryModel().mergeTo));
			branches.add(0, Repository.shortenRefName(getRepositoryModel().mergeTo));

			final Fragment mergeto = new Fragment("mergeto", "mergeToFragment", this);
			mergeto.add(new DropDownChoice<String>("mergeto", this.mergeToModel, branches));
			form.add(mergeto.setVisible(!branches.isEmpty()));
		} else {
			// user can not admin this ticket
			form.add(new Label("responsible").setVisible(false));
			form.add(new Label("milestone").setVisible(false));
			form.add(new Label("mergeto").setVisible(false));
			form.add(new Label("priority").setVisible(false));
		}

		form.add(new AjaxButton("update") {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
				long ticketId = 0L;
				try {
					final String h = WicketUtils.getObject(getPageParameters());
					ticketId = Long.parseLong(h);
				}
				catch (final Exception e) {
					setResponsePage(TicketsPage.class,
							WicketUtils.newRepositoryParameter(EditTicketPage.this.repositoryName));
				}

				TicketModel ticket = app().tickets().getTicket(getRepositoryModel(), ticketId);

				final String createdBy = GitBlitWebSession.get().getUsername();
				final Change change = new Change(createdBy);

				final String title = EditTicketPage.this.titleModel.getObject();
				if (StringUtils.isEmpty(title)) {
					return;
				}

				if (!ticket.title.equals(title)) {
					// title change
					change.setField(Field.title, title);
				}

				final String description = Optional.fromNullable(
						EditTicketPage.this.descriptionEditor.getText()).or("");
				if ((StringUtils.isEmpty(ticket.body) && !StringUtils.isEmpty(description))
						|| (!StringUtils.isEmpty(ticket.body) && !ticket.body.equals(description))) {
					// description change
					change.setField(Field.body, description);
				}

				final Status status = EditTicketPage.this.statusModel.getObject();
				if (!ticket.status.equals(status)) {
					// status change
					change.setField(Field.status, status);
				}

				final Type type = EditTicketPage.this.typeModel.getObject();
				if (!ticket.type.equals(type)) {
					// type change
					change.setField(Field.type, type);
				}

				final String topic = Optional.fromNullable(
						EditTicketPage.this.topicModel.getObject()).or("");
				if ((StringUtils.isEmpty(ticket.topic) && !StringUtils.isEmpty(topic))
						|| (!StringUtils.isEmpty(ticket.topic) && !ticket.topic.equals(topic))) {
					// topic change
					change.setField(Field.topic, topic);
				}

				final TicketResponsible responsible = EditTicketPage.this.responsibleModel == null ? null
						: EditTicketPage.this.responsibleModel.getObject();
				if ((responsible != null) && !responsible.username.equals(ticket.responsible)) {
					// responsible change
					change.setField(Field.responsible, responsible.username);
					if (!StringUtils.isEmpty(responsible.username)) {
						if (!ticket.isWatching(responsible.username)) {
							change.watch(responsible.username);
						}
					}
				}

				final TicketMilestone milestone = EditTicketPage.this.milestoneModel == null ? null
						: EditTicketPage.this.milestoneModel.getObject();
				if ((milestone != null) && !milestone.name.equals(ticket.milestone)) {
					// milestone change
					if (NIL.equals(milestone.name)) {
						change.setField(Field.milestone, "");
					} else {
						change.setField(Field.milestone, milestone.name);
					}
				}

				final TicketModel.Priority priority = EditTicketPage.this.priorityModel.getObject();
				if (!ticket.priority.equals(priority)) {
					change.setField(Field.priority, priority);
				}

				final TicketModel.Severity severity = EditTicketPage.this.severityModel.getObject();
				if (!ticket.severity.equals(severity)) {
					change.setField(Field.severity, severity);
				}

				final String mergeTo = EditTicketPage.this.mergeToModel.getObject();
				if ((StringUtils.isEmpty(ticket.mergeTo) && !StringUtils.isEmpty(mergeTo))
						|| (!StringUtils.isEmpty(mergeTo) && !mergeTo.equals(ticket.mergeTo))) {
					// integration branch change
					change.setField(Field.mergeTo, mergeTo);
				}

				if (change.hasFieldChanges()) {
					if (!ticket.isWatching(createdBy)) {
						change.watch(createdBy);
					}
					ticket = app().tickets().updateTicket(getRepositoryModel(), ticket.number,
							change);
					if (ticket != null) {
						final TicketNotifier notifier = app().tickets().createNotifier();
						notifier.sendMailing(ticket);
						redirectTo(
								TicketsPage.class,
								WicketUtils.newObjectParameter(getRepositoryModel().name, ""
										+ ticket.number));
					} else {
						// TODO error
					}
				} else {
					// nothing to change?!
					redirectTo(
							TicketsPage.class,
							WicketUtils.newObjectParameter(getRepositoryModel().name, ""
									+ ticket.number));
				}
			}
		});

		final Button cancel = new Button("cancel") {
			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
				setResponsePage(TicketsPage.class, getPageParameters());
			}
		};
		cancel.setDefaultFormProcessing(false);
		form.add(cancel);
	}

	@Override
	protected String getPageName() {
		return getString("gb.editTicket");
	}

	@Override
	protected Class<? extends BasePage> getRepoNavPageClass() {
		return TicketsPage.class;
	}
}
