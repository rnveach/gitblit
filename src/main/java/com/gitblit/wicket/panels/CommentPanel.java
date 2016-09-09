/*
 * Copyright 2013 gitblit.com.
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

import org.apache.wicket.PageParameters;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.protocol.http.RequestUtils;
import org.apache.wicket.request.target.basic.RedirectRequestTarget;

import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.UserModel;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.BasePage;

public class CommentPanel extends BasePanel {
	private static final long serialVersionUID = 1L;

	final UserModel user;

	final TicketModel ticket;

	final Change change;

	final Class<? extends BasePage> pageClass;

	private MarkdownTextArea markdownEditor;

	private Label markdownPreview;

	private String repositoryName;

	public CommentPanel(String id, final UserModel user, final TicketModel ticket,
			final Change change, final Class<? extends BasePage> pageClass) {
		super(id);
		this.user = user;
		this.ticket = ticket;
		this.change = change;
		this.pageClass = pageClass;
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();

		final Form<String> form = new Form<String>("editorForm");
		add(form);

		form.add(new AjaxButton("submit", new Model<String>(getString("gb.comment")), form) {
			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit(AjaxRequestTarget target, Form<?> form) {
				final String txt = CommentPanel.this.markdownEditor.getText();
				if (CommentPanel.this.change == null) {
					// new comment
					final Change newComment = new Change(CommentPanel.this.user.username);
					newComment.comment(txt);
					if (!CommentPanel.this.ticket.isWatching(CommentPanel.this.user.username)) {
						newComment.watch(CommentPanel.this.user.username);
					}
					final RepositoryModel repository = app().repositories().getRepositoryModel(
							CommentPanel.this.ticket.repository);
					final TicketModel updatedTicket = app().tickets().updateTicket(repository,
							CommentPanel.this.ticket.number, newComment);
					if (updatedTicket != null) {
						app().tickets().createNotifier().sendMailing(updatedTicket);
						redirectTo(
								CommentPanel.this.pageClass,
								WicketUtils.newObjectParameter(updatedTicket.repository, ""
										+ CommentPanel.this.ticket.number));
					} else {
						error("Failed to add comment!");
					}
				} else {
					// TODO update comment
				}
			}

			/**
			 * Steal from BasePage to realize redirection.
			 * 
			 * @see BasePage
			 * @author krulls@GitHub; ECG Leipzig GmbH, Germany, 2015
			 * 
			 * @param pageClass
			 * @param parameters
			 * @return
			 */
			private void redirectTo(Class<? extends BasePage> pageClass, PageParameters parameters) {
				final String relativeUrl = urlFor(pageClass, parameters).toString();
				final String canonicalUrl = RequestUtils.toAbsolutePath(relativeUrl);
				getRequestCycle().setRequestTarget(new RedirectRequestTarget(canonicalUrl));
			}

		}.setVisible((this.ticket != null) && (this.ticket.number > 0)));

		final IModel<String> markdownPreviewModel = Model.of();
		this.markdownPreview = new Label("markdownPreview", markdownPreviewModel);
		this.markdownPreview.setEscapeModelStrings(false);
		this.markdownPreview.setOutputMarkupId(true);
		add(this.markdownPreview);

		this.markdownEditor = new MarkdownTextArea("markdownEditor", markdownPreviewModel,
				this.markdownPreview);
		this.markdownEditor.setRepository(this.repositoryName);
		WicketUtils.setInputPlaceholder(this.markdownEditor, getString("gb.leaveComment"));
		add(this.markdownEditor);
	}

	public void setRepository(String repositoryName) {
		this.repositoryName = repositoryName;
		if (this.markdownEditor != null) {
			this.markdownEditor.setRepository(repositoryName);
		}
	}
}