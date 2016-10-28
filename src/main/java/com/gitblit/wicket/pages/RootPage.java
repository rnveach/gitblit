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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.wicket.MarkupContainer;
import org.apache.wicket.PageParameters;
import org.apache.wicket.RequestCycle;
import org.apache.wicket.behavior.HeaderContributor;
import org.apache.wicket.markup.html.IHeaderContributor;
import org.apache.wicket.markup.html.IHeaderResponse;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.protocol.http.WebResponse;

import com.gitblit.Constants;
import com.gitblit.Constants.AuthenticationType;
import com.gitblit.Keys;
import com.gitblit.extensions.NavLinkExtension;
import com.gitblit.extensions.UserMenuExtension;
import com.gitblit.models.Menu.ExternalLinkMenuItem;
import com.gitblit.models.Menu.MenuDivider;
import com.gitblit.models.Menu.MenuItem;
import com.gitblit.models.Menu.PageLinkMenuItem;
import com.gitblit.models.Menu.ParameterMenuItem;
import com.gitblit.models.Menu.ToggleMenuItem;
import com.gitblit.models.NavLink;
import com.gitblit.models.NavLink.PageNavLink;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ModelUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.NonTrimmedPasswordTextField;
import com.gitblit.wicket.SessionlessForm;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.AvatarImage;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.NavigationPanel;

/**
 * Root page is a topbar, navigable page like Repositories, Users, or
 * Federation.
 *
 * @author James Moger
 *
 */
public abstract class RootPage extends BasePage {

	boolean showAdmin;

	IModel<String> username = new Model<String>("");
	IModel<String> password = new Model<String>("");
	List<RepositoryModel> repositoryModels = null;

	public RootPage() {
		super();
	}

	public RootPage(PageParameters params) {
		super(params);
	}

	@Override
	protected void setupPage(String repositoryName, String pageName) {

		// CSS header overrides
		add(new HeaderContributor(new IHeaderContributor() {
			private static final long serialVersionUID = 1L;

			@Override
			public void renderHead(IHeaderResponse response) {
				final StringBuilder buffer = new StringBuilder();
				buffer.append("<style type=\"text/css\">\n");
				buffer.append(".navbar-inner {\n");
				final String headerBackground = app().settings()
						.getString(Keys.web.headerBackgroundColor, null);
				if (!StringUtils.isEmpty(headerBackground)) {
					buffer.append(
							MessageFormat.format("background-color: {0};\n", headerBackground));
				}
				final String headerBorder = app().settings().getString(Keys.web.headerBorderColor,
						null);
				if (!StringUtils.isEmpty(headerBorder)) {
					buffer.append(MessageFormat.format("border-bottom: 1px solid {0} !important;\n",
							headerBorder));
				}
				buffer.append("}\n");
				final String headerBorderFocus = app().settings()
						.getString(Keys.web.headerBorderFocusColor, null);
				if (!StringUtils.isEmpty(headerBorderFocus)) {
					buffer.append(".navbar ul li:focus, .navbar .active {\n");
					buffer.append(MessageFormat.format("border-bottom: 4px solid {0};\n",
							headerBorderFocus));
					buffer.append("}\n");
				}
				final String headerForeground = app().settings()
						.getString(Keys.web.headerForegroundColor, null);
				if (!StringUtils.isEmpty(headerForeground)) {
					buffer.append(".navbar ul.nav li a {\n");
					buffer.append(MessageFormat.format("color: {0};\n", headerForeground));
					buffer.append("}\n");
					buffer.append(".navbar ul.nav .active a {\n");
					buffer.append(MessageFormat.format("color: {0};\n", headerForeground));
					buffer.append("}\n");
				}
				final String headerHover = app().settings().getString(Keys.web.headerHoverColor,
						null);
				if (!StringUtils.isEmpty(headerHover)) {
					buffer.append(".navbar ul.nav li a:hover {\n");
					buffer.append(MessageFormat.format("color: {0} !important;\n", headerHover));
					buffer.append("}\n");
				}
				buffer.append("</style>\n");
				response.renderString(buffer.toString());
			}
		}));

		final boolean authenticateView = app().settings().getBoolean(Keys.web.authenticateViewPages,
				false);
		final boolean authenticateAdmin = app().settings()
				.getBoolean(Keys.web.authenticateAdminPages, true);
		final boolean allowAdmin = app().settings().getBoolean(Keys.web.allowAdministration, true);
		final boolean allowLucene = app().settings().getBoolean(Keys.web.allowLuceneIndexing, true);
		final boolean displayUserPanel = app().settings().getBoolean(Keys.web.displayUserPanel,
				true);
		final boolean isLoggedIn = GitBlitWebSession.get().isLoggedIn();

		if (authenticateAdmin) {
			this.showAdmin = allowAdmin && GitBlitWebSession.get().canAdmin();
			// authentication requires state and session
			setStatelessHint(false);
		} else {
			this.showAdmin = allowAdmin;
			if (authenticateView) {
				// authentication requires state and session
				setStatelessHint(false);
			} else {
				// no authentication required, no state and no session required
				setStatelessHint(true);
			}
		}

		if (displayUserPanel && (authenticateView || authenticateAdmin)) {
			if (isLoggedIn) {
				final UserMenu userFragment = new UserMenu("userPanel", "userMenuFragment",
						RootPage.this);
				add(userFragment);
			} else {
				final LoginForm loginForm = new LoginForm("userPanel", "loginFormFragment",
						RootPage.this);
				add(loginForm);
			}
		} else {
			add(new Label("userPanel").setVisible(false));
		}

		// navigation links
		final List<NavLink> navLinks = new ArrayList<NavLink>();
		if (!authenticateView || (authenticateView && isLoggedIn)) {
			UserModel user = UserModel.ANONYMOUS;
			if (isLoggedIn) {
				user = GitBlitWebSession.get().getUser();
			}

			navLinks.add(new PageNavLink(isLoggedIn ? "gb.myDashboard" : "gb.dashboard",
					MyDashboardPage.class, getRootPageParameters()));
			if (isLoggedIn && app().tickets().isReady()) {
				navLinks.add(new PageNavLink("gb.myTickets", MyTicketsPage.class));
			}
			navLinks.add(new PageNavLink("gb.repositories", RepositoriesPage.class,
					getRootPageParameters()));

			navLinks.add(
					new PageNavLink("gb.filestore", FilestorePage.class, getRootPageParameters()));

			navLinks.add(
					new PageNavLink("gb.activity", ActivityPage.class, getRootPageParameters()));
			if (allowLucene) {
				navLinks.add(new PageNavLink("gb.search", LuceneSearchPage.class));
			}

			if (!authenticateView || (authenticateView && isLoggedIn)) {
				addDropDownMenus(navLinks);
			}

			// add nav link extensions
			final List<NavLinkExtension> extensions = app().plugins()
					.getExtensions(NavLinkExtension.class);
			for (final NavLinkExtension ext : extensions) {
				navLinks.addAll(ext.getNavLinks(user));
			}
		}

		final NavigationPanel navPanel = new NavigationPanel("navPanel", getRootNavPageClass(),
				navLinks);
		add(navPanel);

		// display an error message cached from a redirect
		final String cachedMessage = GitBlitWebSession.get().clearErrorMessage();
		if (!StringUtils.isEmpty(cachedMessage)) {
			error(cachedMessage);
		} else if (this.showAdmin) {
			final int pendingProposals = app().federation().getPendingFederationProposals().size();
			if (pendingProposals == 1) {
				info(getString("gb.OneProposalToReview"));
			} else if (pendingProposals > 1) {
				info(MessageFormat.format(getString("gb.nFederationProposalsToReview"),
						pendingProposals));
			}
		}

		super.setupPage(repositoryName, pageName);
	}

	protected Class<? extends BasePage> getRootNavPageClass() {
		return getClass();
	}

	private PageParameters getRootPageParameters() {
		if (reusePageParameters()) {
			final PageParameters pp = getPageParameters();
			if (pp != null) {
				final PageParameters params = new PageParameters(pp);
				// remove named project parameter
				params.remove("p");

				// remove named repository parameter
				params.remove("r");

				// remove named user parameter
				params.remove("user");

				// remove days back parameter if it is the default value
				if (params.containsKey("db") && (params.getInt("db") == app().settings()
						.getInteger(Keys.web.activityDuration, 7))) {
					params.remove("db");
				}
				return params;
			}
		}
		return null;
	}

	protected boolean reusePageParameters() {
		return false;
	}

	private void loginUser(UserModel user) {
		if (user != null) {
			HttpServletRequest request = ((WebRequest) getRequest()).getHttpServletRequest();
			HttpServletResponse response = ((WebResponse) getResponse()).getHttpServletResponse();

			// Set the user into the session
			final GitBlitWebSession session = GitBlitWebSession.get();

			// issue 62: fix session fixation vulnerability
			session.replaceSession();
			session.setUser(user);

			request = ((WebRequest) getRequest()).getHttpServletRequest();
			response = ((WebResponse) getResponse()).getHttpServletResponse();
			request.getSession().setAttribute(Constants.ATTRIB_AUTHTYPE,
					AuthenticationType.CREDENTIALS);

			// Set Cookie
			app().authentication().setCookie(request, response, user);

			if (!session.continueRequest()) {
				final PageParameters params = getPageParameters();
				if (params == null) {
					// redirect to this page
					redirectTo(getClass());
				} else {
					// Strip username and password and redirect to this page
					params.remove("username");
					params.remove("password");
					redirectTo(getClass(), params);
				}
			}
		}
	}

	protected List<RepositoryModel> getRepositoryModels() {
		if (this.repositoryModels == null) {
			final UserModel user = GitBlitWebSession.get().getUser();
			final List<RepositoryModel> repositories = app().repositories()
					.getRepositoryModels(user);
			this.repositoryModels = new ArrayList<RepositoryModel>();
			this.repositoryModels.addAll(repositories);
			Collections.sort(this.repositoryModels);
		}
		return this.repositoryModels;
	}

	protected void addDropDownMenus(List<NavLink> navLinks) {

	}

	protected List<com.gitblit.models.Menu.MenuItem> getRepositoryFilterItems(
			PageParameters params) {
		final UserModel user = GitBlitWebSession.get().getUser();
		final Set<MenuItem> filters = new LinkedHashSet<MenuItem>();
		final List<RepositoryModel> repositories = getRepositoryModels();

		// accessible repositories by federation set
		final Map<String, AtomicInteger> setMap = new HashMap<String, AtomicInteger>();
		for (final RepositoryModel repository : repositories) {
			for (final String set : repository.federationSets) {
				final String key = set.toLowerCase();
				if (setMap.containsKey(key)) {
					setMap.get(key).incrementAndGet();
				} else {
					setMap.put(key, new AtomicInteger(1));
				}
			}
		}
		if (setMap.size() > 0) {
			final List<String> sets = new ArrayList<String>(setMap.keySet());
			Collections.sort(sets);
			for (final String set : sets) {
				filters.add(new ToggleMenuItem(
						MessageFormat.format("{0} ({1})", set, setMap.get(set).get()), "set", set,
						params));
			}
			// divider
			filters.add(new MenuDivider());
		}

		// user's team memberships
		if ((user != null) && (user.teams.size() > 0)) {
			final List<TeamModel> teams = new ArrayList<TeamModel>(user.teams);
			Collections.sort(teams);
			for (final TeamModel team : teams) {
				filters.add(new ToggleMenuItem(
						MessageFormat.format("{0} ({1})", team.name, team.repositories.size()),
						"team", team.name, params));
			}
			// divider
			filters.add(new MenuDivider());
		}

		// custom filters
		final String customFilters = app().settings().getString(Keys.web.customFilters, null);
		if (!StringUtils.isEmpty(customFilters)) {
			boolean addedExpression = false;
			final List<String> expressions = StringUtils.getStringsFromValue(customFilters, "!!!");
			for (final String expression : expressions) {
				if (!StringUtils.isEmpty(expression)) {
					addedExpression = true;
					filters.add(new ToggleMenuItem(null, "x", expression, params));
				}
			}
			// if we added any custom expressions, add a divider
			if (addedExpression) {
				filters.add(new MenuDivider());
			}
		}
		return new ArrayList<MenuItem>(filters);
	}

	protected List<MenuItem> getTimeFilterItems(PageParameters params) {
		// days back choices - additive parameters
		int daysBack = app().settings().getInteger(Keys.web.activityDuration, 7);
		final int maxDaysBack = app().settings().getInteger(Keys.web.activityDurationMaximum, 30);
		if (daysBack < 1) {
			daysBack = 7;
		}
		if (daysBack > maxDaysBack) {
			daysBack = maxDaysBack;
		}
		PageParameters clonedParams;
		if (params == null) {
			clonedParams = new PageParameters();
		} else {
			clonedParams = new PageParameters(params);
		}

		if (!clonedParams.containsKey("db")) {
			clonedParams.put("db", daysBack);
		}

		final List<MenuItem> items = new ArrayList<MenuItem>();
		final Set<Integer> choicesSet = new TreeSet<Integer>(
				app().settings().getIntegers(Keys.web.activityDurationChoices));
		if (choicesSet.isEmpty()) {
			choicesSet.addAll(Arrays.asList(1, 3, 7, 14, 21, 28));
		}
		final List<Integer> choices = new ArrayList<Integer>(choicesSet);
		Collections.sort(choices);
		final String lastDaysPattern = getString("gb.lastNDays");
		for (final Integer db : choices) {
			if (db == 1) {
				items.add(new ParameterMenuItem(getString("gb.time.today"), "db", db.toString(),
						clonedParams));
			} else {
				final String txt = MessageFormat.format(lastDaysPattern, db);
				items.add(new ParameterMenuItem(txt, "db", db.toString(), clonedParams));
			}
		}
		items.add(new MenuDivider());
		return items;
	}

	protected List<RepositoryModel> getRepositories(PageParameters params) {
		if (params == null) {
			return getRepositoryModels();
		}

		boolean hasParameter = false;
		String projectName = WicketUtils.getProjectName(params);
		final String userName = WicketUtils.getUsername(params);
		if (StringUtils.isEmpty(projectName)) {
			if (!StringUtils.isEmpty(userName)) {
				projectName = ModelUtils.getPersonalPath(userName);
			}
		}
		final String repositoryName = WicketUtils.getRepositoryName(params);
		final String set = WicketUtils.getSet(params);
		final String regex = WicketUtils.getRegEx(params);
		final String team = WicketUtils.getTeam(params);
		int daysBack = params.getInt("db", 0);
		final int maxDaysBack = app().settings().getInteger(Keys.web.activityDurationMaximum, 30);

		final List<RepositoryModel> availableModels = getRepositoryModels();
		Set<RepositoryModel> models = new HashSet<RepositoryModel>();

		if (!StringUtils.isEmpty(repositoryName)) {
			// try named repository
			hasParameter = true;
			for (final RepositoryModel model : availableModels) {
				if (model.name.equalsIgnoreCase(repositoryName)) {
					models.add(model);
					break;
				}
			}
		}

		if (!StringUtils.isEmpty(projectName)) {
			// try named project
			hasParameter = true;
			if (projectName.equalsIgnoreCase(
					app().settings().getString(Keys.web.repositoryRootGroupName, "main"))) {
				// root project/group
				for (final RepositoryModel model : availableModels) {
					if (model.name.indexOf('/') == -1) {
						models.add(model);
					}
				}
			} else {
				// named project/group
				final String group = projectName.toLowerCase() + "/";
				for (final RepositoryModel model : availableModels) {
					if (model.name.toLowerCase().startsWith(group)) {
						models.add(model);
					}
				}
			}
		}

		if (!StringUtils.isEmpty(regex)) {
			// filter the repositories by the regex
			hasParameter = true;
			final Pattern pattern = Pattern.compile(regex);
			for (final RepositoryModel model : availableModels) {
				if (pattern.matcher(model.name).find()) {
					models.add(model);
				}
			}
		}

		if (!StringUtils.isEmpty(set)) {
			// filter the repositories by the specified sets
			hasParameter = true;
			final List<String> sets = StringUtils.getStringsFromValue(set, ",");
			for (final RepositoryModel model : availableModels) {
				for (final String curr : sets) {
					if (model.federationSets.contains(curr)) {
						models.add(model);
					}
				}
			}
		}

		if (!StringUtils.isEmpty(team)) {
			// filter the repositories by the specified teams
			hasParameter = true;
			final List<String> teams = StringUtils.getStringsFromValue(team, ",");

			// need TeamModels first
			final List<TeamModel> teamModels = new ArrayList<TeamModel>();
			for (final String name : teams) {
				final TeamModel teamModel = app().users().getTeamModel(name);
				if (teamModel != null) {
					teamModels.add(teamModel);
				}
			}

			// brute-force our way through finding the matching models
			for (final RepositoryModel repositoryModel : availableModels) {
				for (final TeamModel teamModel : teamModels) {
					if (teamModel.hasRepositoryPermission(repositoryModel.name)) {
						models.add(repositoryModel);
					}
				}
			}
		}

		if (!hasParameter) {
			models.addAll(availableModels);
		}

		// time-filter the list
		if (daysBack > 0) {
			if ((maxDaysBack > 0) && (daysBack > maxDaysBack)) {
				daysBack = maxDaysBack;
			}
			final Calendar cal = Calendar.getInstance();
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			cal.add(Calendar.DATE, -1 * daysBack);
			final Date threshold = cal.getTime();
			final Set<RepositoryModel> timeFiltered = new HashSet<RepositoryModel>();
			for (final RepositoryModel model : models) {
				if (model.lastChange.after(threshold)) {
					timeFiltered.add(model);
				}
			}
			models = timeFiltered;
		}

		final List<RepositoryModel> list = new ArrayList<RepositoryModel>(models);
		Collections.sort(list);
		return list;
	}

	/**
	 * Inline login form.
	 */
	private class LoginForm extends Fragment {
		private static final long serialVersionUID = 1L;

		public LoginForm(String id, String markupId, MarkupContainer markupProvider) {
			super(id, markupId, markupProvider);
			setRenderBodyOnly(true);

			final SessionlessForm<Void> loginForm = new SessionlessForm<Void>("loginForm",
					RootPage.this.getClass(), getPageParameters()) {

				private static final long serialVersionUID = 1L;

				@Override
				public void onSubmit() {
					final String username = RootPage.this.username.getObject();
					final char[] password = RootPage.this.password.getObject().toCharArray();

					final HttpServletRequest request = ((WebRequest) RequestCycle.get()
							.getRequest()).getHttpServletRequest();

					UserModel user = app().authentication().authenticate(username, password,
							request.getRemoteAddr());
					if (user == null) {
						error(getString("gb.invalidUsernameOrPassword"));
					} else if (user.username.equals(Constants.FEDERATION_USER)) {
						// disallow the federation user from logging in via the
						// web ui
						error(getString("gb.invalidUsernameOrPassword"));
						user = null;
					} else {
						loginUser(user);
					}
				}
			};
			final TextField<String> unameField = new TextField<String>("username",
					RootPage.this.username);
			WicketUtils.setInputPlaceholder(unameField, markupProvider.getString("gb.username"));
			loginForm.add(unameField);
			final NonTrimmedPasswordTextField pwField = new NonTrimmedPasswordTextField("password",
					RootPage.this.password);
			WicketUtils.setInputPlaceholder(pwField, markupProvider.getString("gb.password"));
			loginForm.add(pwField);
			add(loginForm);
		}
	}

	/**
	 * Menu for the authenticated user.
	 */
	class UserMenu extends Fragment {

		private static final long serialVersionUID = 1L;

		public UserMenu(String id, String markupId, MarkupContainer markupProvider) {
			super(id, markupId, markupProvider);
			setRenderBodyOnly(true);
		}

		@Override
		protected void onInitialize() {
			super.onInitialize();

			final GitBlitWebSession session = GitBlitWebSession.get();
			final UserModel user = session.getUser();
			final boolean editCredentials = app().authentication().supportsCredentialChanges(user);
			final HttpServletRequest request = ((WebRequest) getRequest()).getHttpServletRequest();
			final AuthenticationType authenticationType = (AuthenticationType) request
					.getAttribute(Constants.ATTRIB_AUTHTYPE);
			final boolean standardLogin = (null != authenticationType)
					? authenticationType.isStandard() : true;

			if (app().settings().getBoolean(Keys.web.allowGravatar, true)) {
				add(new AvatarImage("username", user, "navbarGravatar", 20, false));
			} else {
				add(new Label("username", user.getDisplayName()));
			}

			final List<MenuItem> standardItems = new ArrayList<MenuItem>();
			standardItems.add(new MenuDivider());
			if (user.canAdmin() || user.canCreate()) {
				standardItems.add(
						new PageLinkMenuItem("gb.newRepository", app().getNewRepositoryPage()));
			}
			standardItems.add(new PageLinkMenuItem("gb.myProfile", UserPage.class,
					WicketUtils.newUsernameParameter(user.username)));
			if (editCredentials) {
				standardItems
						.add(new PageLinkMenuItem("gb.changePassword", ChangePasswordPage.class));
			}
			standardItems.add(new MenuDivider());
			add(newSubmenu("standardMenu", user.getDisplayName(), standardItems));

			if (RootPage.this.showAdmin) {
				// admin menu
				final List<MenuItem> adminItems = new ArrayList<MenuItem>();
				adminItems.add(new MenuDivider());
				adminItems.add(new PageLinkMenuItem("gb.users", UsersPage.class));
				adminItems.add(new PageLinkMenuItem("gb.teams", TeamsPage.class));

				final boolean showRegistrations = app().federation().canFederate()
						&& app().settings().getBoolean(Keys.web.showFederationRegistrations, false);
				if (showRegistrations) {
					adminItems.add(new PageLinkMenuItem("gb.federation", FederationPage.class));
				}
				adminItems.add(new MenuDivider());

				add(newSubmenu("adminMenu", getString("gb.administration"), adminItems));
			} else {
				add(new Label("adminMenu").setVisible(false));
			}

			// plugin extension items
			final List<MenuItem> extensionItems = new ArrayList<MenuItem>();
			final List<UserMenuExtension> extensions = app().plugins()
					.getExtensions(UserMenuExtension.class);
			for (final UserMenuExtension ext : extensions) {
				final List<MenuItem> items = ext.getMenuItems(user);
				extensionItems.addAll(items);
			}

			if (extensionItems.isEmpty()) {
				// no extension items
				add(new Label("extensionsMenu").setVisible(false));
			} else {
				// found extension items
				extensionItems.add(0, new MenuDivider());
				add(newSubmenu("extensionsMenu", getString("gb.extensions"), extensionItems));
				extensionItems.add(new MenuDivider());
			}

			add(new BookmarkablePageLink<Void>("logout", LogoutPage.class)
					.setVisible(standardLogin));
		}

		/**
		 * Creates a submenu. This is not actually submenu because we're using
		 * an older Twitter Bootstrap which is pre-submenu.
		 *
		 * @param wicketId
		 * @param submenuTitle
		 * @param menuItems
		 * @return a submenu fragment
		 */
		private Fragment newSubmenu(String wicketId, String submenuTitle,
				List<MenuItem> menuItems) {
			final Fragment submenu = new Fragment(wicketId, "submenuFragment", this);
			submenu.add(new Label("submenuTitle", submenuTitle).setRenderBodyOnly(true));
			final ListDataProvider<MenuItem> menuItemsDp = new ListDataProvider<MenuItem>(
					menuItems);
			final DataView<MenuItem> submenuItems = new DataView<MenuItem>("submenuItem",
					menuItemsDp) {
				private static final long serialVersionUID = 1L;

				@Override
				public void populateItem(final Item<MenuItem> menuItem) {
					final MenuItem item = menuItem.getModelObject();
					String name = item.toString();
					try {
						// try to lookup translation
						name = getString(name);
					}
					catch (final Exception e) {
					}
					if (item instanceof PageLinkMenuItem) {
						// link to another Wicket page
						final PageLinkMenuItem pageLink = (PageLinkMenuItem) item;
						menuItem.add(new LinkPanel("submenuLink", null, null, name,
								pageLink.getPageClass(), pageLink.getPageParameters(), false)
										.setRenderBodyOnly(true));
					} else if (item instanceof ExternalLinkMenuItem) {
						// link to a specified href
						final ExternalLinkMenuItem extLink = (ExternalLinkMenuItem) item;
						menuItem.add(new LinkPanel("submenuLink", null, name, extLink.getHref(),
								extLink.openInNewWindow()).setRenderBodyOnly(true));
					} else if (item instanceof MenuDivider) {
						// divider
						menuItem.add(new Label("submenuLink").setRenderBodyOnly(true));
						WicketUtils.setCssClass(menuItem, "divider");
					}
				}
			};
			submenu.add(submenuItems);
			submenu.setRenderBodyOnly(true);
			return submenu;
		}
	}
}
