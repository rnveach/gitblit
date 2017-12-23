package com.gitblit.wicket;

import org.apache.wicket.Component;
import org.apache.wicket.behavior.Behavior;
import org.apache.wicket.markup.html.IHeaderContributor;
import org.apache.wicket.markup.html.IHeaderResponse;

public class HeaderContributor extends Behavior {
	private final IHeaderContributor headerContributor;

	public HeaderContributor(IHeaderContributor headerContributor) {
		this.headerContributor = headerContributor;
	}

	@Override
	public void renderHead(Component component, IHeaderResponse response) {
		if (!response.wasRendered(headerContributor)) {
			headerContributor.renderHead(response);
			response.markRendered(headerContributor);
		}
	}
}
