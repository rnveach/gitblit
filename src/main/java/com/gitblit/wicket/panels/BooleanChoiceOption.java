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

import java.util.List;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.model.IModel;
import org.parboiled.common.StringUtils;

/**
 * A re-usable conditional choice option panel.
 *
 * [x] title description [choices]
 *
 * @author James Moger
 *
 */
public class BooleanChoiceOption<T> extends BasePanel {

	private static final long serialVersionUID = 1L;

	final CheckBox checkbox;
	final DropDownChoice<T> choice;

	public BooleanChoiceOption(String wicketId, String title, String description,
			IModel<Boolean> checkboxModel, IModel<T> choiceModel, List<T> choices) {
		super(wicketId);
		add(new Label("name", title));
		add(new Label("description", description).setVisible(!StringUtils.isEmpty(description)));

		this.checkbox = new CheckBox("checkbox", checkboxModel);
		this.checkbox.setOutputMarkupId(true);

		this.choice = new DropDownChoice<T>("choice", choiceModel, choices);
		this.choice.setOutputMarkupId(true);

		setup();
	}

	private void setup() {
		add(this.checkbox);
		add(this.choice.setMarkupId("choice").setEnabled(this.choice.getChoices().size() > 0));
		this.choice.setEnabled(this.checkbox.getModelObject());

		this.checkbox.add(new AjaxFormComponentUpdatingBehavior("onchange") {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				BooleanChoiceOption.this.choice.setEnabled(BooleanChoiceOption.this.checkbox
						.getModelObject());
				target.addComponent(BooleanChoiceOption.this.choice);
				if (!BooleanChoiceOption.this.choice.isEnabled()) {
					BooleanChoiceOption.this.choice.setModelObject(null);
				}
			}
		});
	}
}
