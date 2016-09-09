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

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.target.basic.RedirectRequestTarget;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.GitBlitException;
import com.gitblit.Keys;
import com.gitblit.extensions.RepositoryNavLinkExtension;
import com.gitblit.models.NavLink;
import com.gitblit.models.NavLink.ExternalNavLink;
import com.gitblit.models.NavLink.PageNavLink;
import com.gitblit.models.ProjectModel;
import com.gitblit.models.RefModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.SubmoduleModel;
import com.gitblit.models.UserModel;
import com.gitblit.models.UserRepositoryPreferences;
import com.gitblit.servlet.PagesServlet;
import com.gitblit.servlet.SyndicationServlet;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.BugtraqProcessor;
import com.gitblit.utils.DeepCopier;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.ModelUtils;
import com.gitblit.utils.RefLogUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.SessionlessForm;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.NavigationPanel;
import com.gitblit.wicket.panels.RefsPanel;
import com.google.common.base.Optional;

public abstract class RepositoryPage extends RootPage {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	private final String PARAM_STAR = "star";

	protected final String projectName;
	protected final String repositoryName;
	protected final String objectId;

	private transient Repository r;

	private RepositoryModel m;

	private Map<String, SubmoduleModel> submodules;

	private boolean showAdmin;
	private final boolean isOwner;

	public RepositoryPage(PageParameters params) {
		super(params);
		this.repositoryName = WicketUtils.getRepositoryName(params);
		final String root = StringUtils.getFirstPathElement(this.repositoryName);
		if (StringUtils.isEmpty(root)) {
			this.projectName = app().settings().getString(Keys.web.repositoryRootGroupName, "main");
		} else {
			this.projectName = root;
		}
		this.objectId = WicketUtils.getObject(params);

		if (StringUtils.isEmpty(this.repositoryName)) {
			error(MessageFormat.format(getString("gb.repositoryNotSpecifiedFor"), getPageName()),
					true);
		}

		if (!getRepositoryModel().hasCommits && (getClass() != EmptyRepositoryPage.class)) {
			throw new RestartResponseException(EmptyRepositoryPage.class, params);
		}

		if (getRepositoryModel().isCollectingGarbage) {
			error(MessageFormat.format(getString("gb.busyCollectingGarbage"),
					getRepositoryModel().name), true);
		}

		if (this.objectId != null) {
			RefModel branch = null;
			if ((branch = JGitUtils.getBranch(getRepository(), this.objectId)) != null) {
				UserModel user = GitBlitWebSession.get().getUser();
				if (user == null) {
					// workaround until get().getUser() is reviewed throughout
					// the app
					user = UserModel.ANONYMOUS;
				}
				final boolean canAccess = user.canView(getRepositoryModel(),
						branch.reference.getName());
				if (!canAccess) {
					error(getString("gb.accessDenied"), true);
				}
			}
		}

		if (params.containsKey(this.PARAM_STAR)) {
			// set starred state
			final boolean star = params.getBoolean(this.PARAM_STAR);
			final UserModel user = GitBlitWebSession.get().getUser();
			if ((user != null) && user.isAuthenticated) {
				final UserRepositoryPreferences prefs = user.getPreferences()
						.getRepositoryPreferences(getRepositoryModel().name);
				prefs.starred = star;
				try {
					app().gitblit().reviseUser(user.username, user);
				}
				catch (final GitBlitException e) {
					this.logger.error("Failed to update user " + user.username, e);
					error(getString("gb.failedToUpdateUser"), false);
				}
			}
		}

		this.showAdmin = false;
		if (app().settings().getBoolean(Keys.web.authenticateAdminPages, true)) {
			final boolean allowAdmin = app().settings().getBoolean(Keys.web.allowAdministration,
					false);
			this.showAdmin = allowAdmin && GitBlitWebSession.get().canAdmin();
		} else {
			this.showAdmin = app().settings().getBoolean(Keys.web.allowAdministration, false);
		}
		this.isOwner = GitBlitWebSession.get().isLoggedIn()
				&& (getRepositoryModel().isOwner(GitBlitWebSession.get().getUsername()));

		// register the available navigation links for this page and user
		final List<NavLink> navLinks = registerNavLinks();

		// standard navigation links
		final NavigationPanel navigationPanel = new NavigationPanel("repositoryNavPanel",
				getRepoNavPageClass(), navLinks);
		add(navigationPanel);

		add(new ExternalLink("syndication", SyndicationServlet.asLink(getRequest()
				.getRelativePathPrefixToContextRoot(), getRepositoryName(), null, 0)));

		// add floating search form
		final SearchForm searchForm = new SearchForm("searchForm", getRepositoryName());
		add(searchForm);
		searchForm.setTranslatedAttributes();

		// set stateless page preference
		setStatelessHint(true);
	}

	@Override
	protected Class<? extends BasePage> getRootNavPageClass() {
		return RepositoriesPage.class;
	}

	protected Class<? extends BasePage> getRepoNavPageClass() {
		return getClass();
	}

	protected BugtraqProcessor bugtraqProcessor() {
		return new BugtraqProcessor(app().settings());
	}

	private List<NavLink> registerNavLinks() {
		final Repository r = getRepository();
		final RepositoryModel model = getRepositoryModel();

		PageParameters params = null;
		PageParameters objectParams = null;
		if (!StringUtils.isEmpty(this.repositoryName)) {
			params = WicketUtils.newRepositoryParameter(getRepositoryName());
			objectParams = params;

			// preserve the objectid iff the objectid directly (or indirectly)
			// refers to a ref
			if (isCommitPage() && !StringUtils.isEmpty(this.objectId)) {
				final RevCommit commit = JGitUtils.getCommit(r, this.objectId);
				if (commit != null) {
					final String bestId = getBestCommitId(commit);
					if (!commit.getName().equals(bestId)) {
						objectParams = WicketUtils.newObjectParameter(getRepositoryName(), bestId);
					}
				}
			}
		}
		final List<NavLink> navLinks = new ArrayList<NavLink>();

		// standard links
		if (RefLogUtils.getRefLogBranch(r) == null) {
			navLinks.add(new PageNavLink("gb.summary", SummaryPage.class, params));
		} else {
			navLinks.add(new PageNavLink("gb.summary", SummaryPage.class, params));
			// pages.put("overview", new PageRegistration("gb.overview",
			// OverviewPage.class, params));
			navLinks.add(new PageNavLink("gb.reflog", ReflogPage.class, params));
		}

		if (!model.hasCommits) {
			return navLinks;
		}

		navLinks.add(new PageNavLink("gb.commits", LogPage.class, objectParams));
		navLinks.add(new PageNavLink("gb.tree", TreePage.class, objectParams));
		if (app().tickets().isReady()
				&& (app().tickets().isAcceptingNewTickets(model) || app().tickets().hasTickets(
						model))) {
			final PageParameters tParams = WicketUtils.newOpenTicketsParameter(getRepositoryName());
			navLinks.add(new PageNavLink("gb.tickets", TicketsPage.class, tParams));
		}
		navLinks.add(new PageNavLink("gb.docs", DocsPage.class, objectParams, true));
		if (app().settings().getBoolean(Keys.web.allowForking, true)) {
			navLinks.add(new PageNavLink("gb.forks", ForksPage.class, params, true));
		}
		navLinks.add(new PageNavLink("gb.compare", ComparePage.class, params, true));

		// conditional links
		// per-repository extra navlinks
		if (JGitUtils.getPagesBranch(r) != null) {
			final ExternalNavLink pagesLink = new ExternalNavLink("gb.pages", PagesServlet.asLink(
					getRequest().getRelativePathPrefixToContextRoot(), getRepositoryName(), null),
					true);
			navLinks.add(pagesLink);
		}

		UserModel user = UserModel.ANONYMOUS;
		if (GitBlitWebSession.get().isLoggedIn()) {
			user = GitBlitWebSession.get().getUser();
		}

		// add repository nav link extensions
		final List<RepositoryNavLinkExtension> extensions = app().plugins().getExtensions(
				RepositoryNavLinkExtension.class);
		for (final RepositoryNavLinkExtension ext : extensions) {
			navLinks.addAll(ext.getNavLinks(user, model));
		}

		return navLinks;
	}

	protected boolean allowForkControls() {
		return app().settings().getBoolean(Keys.web.allowForking, true);
	}

	@Override
	protected void setupPage(String repositoryName, String pageName) {

		// This method should only be called once in the page lifecycle.
		// However, it must be called after the constructor has run, hence not
		// in onInitialize
		// It may be attempted to be called again if an info or error message is
		// displayed.
		if (get("projectTitle") != null) {
			return;
		}

		final String projectName = StringUtils.getFirstPathElement(repositoryName);
		final ProjectModel project = app().projects().getProjectModel(projectName);

		if (project.isUserProject()) {
			// user-as-project
			add(new LinkPanel("projectTitle", null, project.getDisplayName(), UserPage.class,
					WicketUtils.newUsernameParameter(project.name.substring(1))));
		} else {
			// project
			add(new LinkPanel("projectTitle", null, project.name, ProjectPage.class,
					WicketUtils.newProjectParameter(project.name)));
		}

		String name = StringUtils.stripDotGit(repositoryName);
		if (!StringUtils.isEmpty(projectName) && name.startsWith(projectName)) {
			name = name.substring(projectName.length() + 1);
		}
		add(new LinkPanel("repositoryName", null, name, SummaryPage.class,
				WicketUtils.newRepositoryParameter(repositoryName)));

		UserModel user = GitBlitWebSession.get().getUser();
		if (user == null) {
			user = UserModel.ANONYMOUS;
		}

		// indicate origin repository
		final RepositoryModel model = getRepositoryModel();
		if (StringUtils.isEmpty(model.originRepository)) {
			if (model.isMirror) {
				add(new Fragment("repoIcon", "mirrorIconFragment", this));
				final Fragment mirrorFrag = new Fragment("originRepository", "mirrorFragment", this);
				final Label lbl = new Label("originRepository", MessageFormat.format(
						getString("gb.mirrorOf"), "<b>" + model.origin + "</b>"));
				mirrorFrag.add(lbl.setEscapeModelStrings(false));
				add(mirrorFrag);
			} else {
				if (model.isBare) {
					add(new Fragment("repoIcon", "repoIconFragment", this));
				} else {
					add(new Fragment("repoIcon", "cloneIconFragment", this));
				}
				add(new Label("originRepository", Optional.of(model.description).or("")));
			}
		} else {
			final RepositoryModel origin = app().repositories().getRepositoryModel(
					model.originRepository);
			if (origin == null) {
				// no origin repository, show description if available
				if (model.isBare) {
					add(new Fragment("repoIcon", "repoIconFragment", this));
				} else {
					add(new Fragment("repoIcon", "cloneIconFragment", this));
				}
				add(new Label("originRepository", Optional.of(model.description).or("")));
			} else if (!user.canView(origin)) {
				// show origin repository without link
				add(new Fragment("repoIcon", "forkIconFragment", this));
				final Fragment forkFrag = new Fragment("originRepository", "originFragment", this);
				forkFrag.add(new Label("originRepository", StringUtils
						.stripDotGit(model.originRepository)));
				add(forkFrag);
			} else {
				// link to origin repository
				add(new Fragment("repoIcon", "forkIconFragment", this));
				final Fragment forkFrag = new Fragment("originRepository", "originFragment", this);
				forkFrag.add(new LinkPanel("originRepository", null, StringUtils
						.stripDotGit(model.originRepository), SummaryPage.class, WicketUtils
						.newRepositoryParameter(model.originRepository)));
				add(forkFrag);
			}
		}

		// new ticket button
		if (user.isAuthenticated && app().tickets().isAcceptingNewTickets(getRepositoryModel())) {
			final String newTicketUrl = getRequestCycle().urlFor(NewTicketPage.class,
					WicketUtils.newRepositoryParameter(repositoryName)).toString();
			addToolbarButton("newTicketLink", "fa fa-ticket", getString("gb.new"), newTicketUrl);
		} else {
			add(new Label("newTicketLink").setVisible(false));
		}

		// (un)star link allows a user to star a repository
		if (user.isAuthenticated && model.hasCommits) {
			final PageParameters starParams = DeepCopier.copy(getPageParameters());
			starParams.put(this.PARAM_STAR, !user.getPreferences().isStarredRepository(model.name));
			final String toggleStarUrl = getRequestCycle().urlFor(getClass(), starParams)
					.toString();
			if (user.getPreferences().isStarredRepository(model.name)) {
				// show unstar button
				add(new Label("starLink").setVisible(false));
				addToolbarButton("unstarLink", "icon-star-empty", getString("gb.unstar"),
						toggleStarUrl);
			} else {
				// show star button
				addToolbarButton("starLink", "icon-star", getString("gb.star"), toggleStarUrl);
				add(new Label("unstarLink").setVisible(false));
			}
		} else {
			// anonymous user
			add(new Label("starLink").setVisible(false));
			add(new Label("unstarLink").setVisible(false));
		}

		// fork controls
		if (!allowForkControls() || !user.isAuthenticated) {
			// must be logged-in to fork, hide all fork controls
			add(new ExternalLink("forkLink", "").setVisible(false));
			add(new ExternalLink("myForkLink", "").setVisible(false));
		} else {
			final String fork = app().repositories().getFork(user.username, model.name);
			final String userRepo = ModelUtils.getPersonalPath(user.username) + "/"
					+ StringUtils.stripDotGit(StringUtils.getLastPathElement(model.name));
			final boolean hasUserRepo = app().repositories().hasRepository(userRepo);
			final boolean hasFork = fork != null;
			final boolean canFork = user.canFork(model) && model.hasCommits && !hasUserRepo;

			if (hasFork || !canFork) {
				// user not allowed to fork or fork already exists or repo
				// forbids forking
				add(new ExternalLink("forkLink", "").setVisible(false));

				if (hasFork && !fork.equals(model.name)) {
					// user has fork, view my fork link
					final String url = getRequestCycle().urlFor(SummaryPage.class,
							WicketUtils.newRepositoryParameter(fork)).toString();
					add(new ExternalLink("myForkLink", url));
				} else {
					// no fork, hide view my fork link
					add(new ExternalLink("myForkLink", "").setVisible(false));
				}
			} else if (canFork) {
				// can fork and we do not have one
				add(new ExternalLink("myForkLink", "").setVisible(false));
				final String url = getRequestCycle().urlFor(ForkPage.class,
						WicketUtils.newRepositoryParameter(model.name)).toString();
				add(new ExternalLink("forkLink", url));
			}
		}

		if (this.showAdmin || this.isOwner) {
			final String url = getRequestCycle().urlFor(EditRepositoryPage.class,
					WicketUtils.newRepositoryParameter(model.name)).toString();
			add(new ExternalLink("editLink", url));
		} else {
			add(new Label("editLink").setVisible(false));
		}

		super.setupPage(repositoryName, pageName);
	}

	protected void addToolbarButton(String wicketId, String iconClass, String label, String url) {
		final Fragment button = new Fragment(wicketId, "toolbarLinkFragment", this);
		final Label icon = new Label("icon");
		WicketUtils.setCssClass(icon, iconClass);
		button.add(icon);
		button.add(new Label("label", label));
		button.add(new SimpleAttributeModifier("href", url));
		add(button);
	}

	protected void addSyndicationDiscoveryLink() {
		add(WicketUtils.syndicationDiscoveryLink(SyndicationServlet.getTitle(this.repositoryName,
				this.objectId), SyndicationServlet.asLink(getRequest()
				.getRelativePathPrefixToContextRoot(), this.repositoryName, this.objectId, 0)));
	}

	protected Repository getRepository() {
		if (this.r == null) {
			final Repository r = app().repositories().getRepository(this.repositoryName);
			if (r == null) {
				error(getString("gb.canNotLoadRepository") + " " + this.repositoryName, true);
				return null;
			}
			this.r = r;
		}
		return this.r;
	}

	protected RepositoryModel getRepositoryModel() {
		if (this.m == null) {
			final RepositoryModel model = app().repositories().getRepositoryModel(
					GitBlitWebSession.get().getUser(), this.repositoryName);
			if (model == null) {
				if (app().repositories().hasRepository(this.repositoryName, true)) {
					// has repository, but unauthorized
					authenticationError(getString("gb.unauthorizedAccessForRepository") + " "
							+ this.repositoryName);
				} else {
					// does not have repository
					error(getString("gb.canNotLoadRepository") + " " + this.repositoryName, true);
				}
				return null;
			}
			this.m = model;
		}
		return this.m;
	}

	protected String getRepositoryName() {
		return getRepositoryModel().name;
	}

	protected RevCommit getCommit() {
		final RevCommit commit = JGitUtils.getCommit(this.r, this.objectId);
		if (commit == null) {
			error(MessageFormat.format(getString("gb.failedToFindCommit"), this.objectId,
					this.repositoryName, getPageName()), null, LogPage.class,
					WicketUtils.newRepositoryParameter(this.repositoryName));
		}
		getSubmodules(commit);
		return commit;
	}

	protected String getBestCommitId(RevCommit commit) {
		String head = null;
		try {
			head = this.r.resolve(getRepositoryModel().HEAD).getName();
		}
		catch (final Exception e) {
		}

		final String id = commit.getName();
		if (!StringUtils.isEmpty(head) && head.equals(id)) {
			// match default branch
			return Repository.shortenRefName(getRepositoryModel().HEAD);
		}

		// find first branch match
		for (final RefModel ref : JGitUtils.getLocalBranches(this.r, false, -1)) {
			if (ref.getObjectId().getName().equals(id)) {
				return Repository.shortenRefName(ref.getName());
			}
		}

		// return sha
		return id;
	}

	protected Map<String, SubmoduleModel> getSubmodules(RevCommit commit) {
		if (this.submodules == null) {
			this.submodules = new HashMap<String, SubmoduleModel>();
			for (final SubmoduleModel model : JGitUtils.getSubmodules(this.r, commit.getTree())) {
				this.submodules.put(model.path, model);
			}
		}
		return this.submodules;
	}

	protected SubmoduleModel getSubmodule(String path) {
		SubmoduleModel model = null;
		if (this.submodules != null) {
			model = this.submodules.get(path);
		}
		if (model == null) {
			// undefined submodule?!
			model = new SubmoduleModel(path.substring(path.lastIndexOf('/') + 1), path, path);
			model.hasSubmodule = false;
			model.gitblitPath = model.name;
			return model;
		} else {
			// extract the repository name from the clone url
			final List<String> patterns = app().settings()
					.getStrings(Keys.git.submoduleUrlPatterns);
			final String submoduleName = StringUtils.extractRepositoryPath(model.url,
					patterns.toArray(new String[0]));

			// determine the current path for constructing paths relative
			// to the current repository
			String currentPath = "";
			if (this.repositoryName.indexOf('/') > -1) {
				currentPath = this.repositoryName.substring(0,
						this.repositoryName.lastIndexOf('/') + 1);
			}

			// try to locate the submodule repository
			// prefer bare to non-bare names
			final List<String> candidates = new ArrayList<String>();

			// relative
			candidates.add(currentPath + StringUtils.stripDotGit(submoduleName));
			candidates.add(candidates.get(candidates.size() - 1) + ".git");

			// relative, no subfolder
			if (submoduleName.lastIndexOf('/') > -1) {
				final String name = submoduleName.substring(submoduleName.lastIndexOf('/') + 1);
				candidates.add(currentPath + StringUtils.stripDotGit(name));
				candidates.add(candidates.get(candidates.size() - 1) + ".git");
			}

			// absolute
			candidates.add(StringUtils.stripDotGit(submoduleName));
			candidates.add(candidates.get(candidates.size() - 1) + ".git");

			// absolute, no subfolder
			if (submoduleName.lastIndexOf('/') > -1) {
				final String name = submoduleName.substring(submoduleName.lastIndexOf('/') + 1);
				candidates.add(StringUtils.stripDotGit(name));
				candidates.add(candidates.get(candidates.size() - 1) + ".git");
			}

			// create a unique, ordered set of candidate paths
			final Set<String> paths = new LinkedHashSet<String>(candidates);
			for (final String candidate : paths) {
				if (app().repositories().hasRepository(candidate)) {
					model.hasSubmodule = true;
					model.gitblitPath = candidate;
					return model;
				}
			}

			// we do not have a copy of the submodule, but we need a path
			model.gitblitPath = candidates.get(0);
			return model;
		}
	}

	protected String getShortObjectId(String objectId) {
		return objectId.substring(0, app().settings().getInteger(Keys.web.shortCommitIdLength, 6));
	}

	protected void addRefs(Repository r, RevCommit c) {
		add(new RefsPanel("refsPanel", this.repositoryName, c, JGitUtils.getAllRefs(r,
				getRepositoryModel().showRemoteBranches)));
	}

	protected void addFullText(String wicketId, String text) {
		final RepositoryModel model = getRepositoryModel();
		final String content = bugtraqProcessor().processCommitMessage(this.r, model, text);
		String html;
		switch (model.commitMessageRenderer) {
		case MARKDOWN:
			final String safeContent = app().xssFilter().relaxed(content);
			html = MessageFormat.format("<div class='commit_message'>{0}</div>", safeContent);
			break;
		default:
			html = MessageFormat.format("<pre class='commit_message'>{0}</pre>", content);
			break;
		}
		add(new Label(wicketId, html).setEscapeModelStrings(false));
	}

	protected abstract String getPageName();

	protected boolean isCommitPage() {
		return false;
	}

	protected Component createPersonPanel(String wicketId, PersonIdent identity,
			Constants.SearchType searchType) {
		String name = identity == null ? "" : identity.getName();
		String address = identity == null ? "" : identity.getEmailAddress();
		name = StringUtils.removeNewlines(name);
		address = StringUtils.removeNewlines(address);
		final boolean showEmail = app().settings().getBoolean(Keys.web.showEmailAddresses, false);
		if (!showEmail || StringUtils.isEmpty(name) || StringUtils.isEmpty(address)) {
			String value = name;
			if (StringUtils.isEmpty(value)) {
				if (showEmail) {
					value = address;
				} else {
					value = getString("gb.missingUsername");
				}
			}
			final Fragment partial = new Fragment(wicketId, "partialPersonIdent", this);
			final LinkPanel link = new LinkPanel("personName", "list", value, GitSearchPage.class,
					WicketUtils.newSearchParameter(this.repositoryName, this.objectId, value,
							searchType));
			setPersonSearchTooltip(link, value, searchType);
			partial.add(link);
			return partial;
		} else {
			final Fragment fullPerson = new Fragment(wicketId, "fullPersonIdent", this);
			final LinkPanel nameLink = new LinkPanel("personName", "list", name,
					GitSearchPage.class, WicketUtils.newSearchParameter(this.repositoryName,
							this.objectId, name, searchType));
			setPersonSearchTooltip(nameLink, name, searchType);
			fullPerson.add(nameLink);

			final LinkPanel addressLink = new LinkPanel("personAddress", "hidden-phone list", "<"
					+ address + ">", GitSearchPage.class, WicketUtils.newSearchParameter(
					this.repositoryName, this.objectId, address, searchType));
			setPersonSearchTooltip(addressLink, address, searchType);
			fullPerson.add(addressLink);
			return fullPerson;
		}
	}

	protected void setPersonSearchTooltip(Component component, String value,
			Constants.SearchType searchType) {
		if (searchType.equals(Constants.SearchType.AUTHOR)) {
			WicketUtils.setHtmlTooltip(component, getString("gb.searchForAuthor") + " " + value);
		} else if (searchType.equals(Constants.SearchType.COMMITTER)) {
			WicketUtils.setHtmlTooltip(component, getString("gb.searchForCommitter") + " " + value);
		}
	}

	protected void setChangeTypeTooltip(Component container, ChangeType type) {
		switch (type) {
		case ADD:
			WicketUtils.setHtmlTooltip(container, getString("gb.addition"));
			break;
		case COPY:
		case RENAME:
			WicketUtils.setHtmlTooltip(container, getString("gb.rename"));
			break;
		case DELETE:
			WicketUtils.setHtmlTooltip(container, getString("gb.deletion"));
			break;
		case MODIFY:
			WicketUtils.setHtmlTooltip(container, getString("gb.modification"));
			break;
		}
	}

	@Override
	protected void onBeforeRender() {
		// dispose of repository object
		if (this.r != null) {
			this.r.close();
			this.r = null;
		}

		// setup page header and footer
		setupPage(getRepositoryName(), "/ " + getPageName());

		super.onBeforeRender();
	}

	@Override
	protected void setLastModified() {
		if (getClass().isAnnotationPresent(CacheControl.class)) {
			final CacheControl cacheControl = getClass().getAnnotation(CacheControl.class);
			switch (cacheControl.value()) {
			case REPOSITORY:
				final RepositoryModel repository = getRepositoryModel();
				if (repository != null) {
					setLastModified(repository.lastChange);
				}
				break;
			case COMMIT:
				final RevCommit commit = getCommit();
				if (commit != null) {
					final Date commitDate = JGitUtils.getCommitDate(commit);
					setLastModified(commitDate);
				}
				break;
			default:
				super.setLastModified();
			}
		}
	}

	protected PageParameters newRepositoryParameter() {
		return WicketUtils.newRepositoryParameter(this.repositoryName);
	}

	protected PageParameters newCommitParameter() {
		return WicketUtils.newObjectParameter(this.repositoryName, this.objectId);
	}

	protected PageParameters newCommitParameter(String commitId) {
		return WicketUtils.newObjectParameter(this.repositoryName, commitId);
	}

	public boolean isShowAdmin() {
		return this.showAdmin;
	}

	public boolean isOwner() {
		return this.isOwner;
	}

	private class SearchForm extends SessionlessForm<Void> implements Serializable {
		private static final long serialVersionUID = 1L;

		private final String repositoryName;

		private final IModel<String> searchBoxModel = new Model<String>("");

		private final IModel<Constants.SearchType> searchTypeModel = new Model<Constants.SearchType>(
				Constants.SearchType.COMMIT);

		public SearchForm(String id, String repositoryName) {
			super(id, RepositoryPage.this.getClass(), getPageParameters());
			this.repositoryName = repositoryName;
			final DropDownChoice<Constants.SearchType> searchType = new DropDownChoice<Constants.SearchType>(
					"searchType", Arrays.asList(Constants.SearchType.values()));
			searchType.setModel(this.searchTypeModel);
			add(searchType.setVisible(app().settings().getBoolean(Keys.web.showSearchTypeSelection,
					false)));
			final TextField<String> searchBox = new TextField<String>("searchBox",
					this.searchBoxModel);
			add(searchBox);
		}

		void setTranslatedAttributes() {
			WicketUtils.setHtmlTooltip(get("searchType"), getString("gb.searchTypeTooltip"));
			WicketUtils.setHtmlTooltip(get("searchBox"),
					MessageFormat.format(getString("gb.searchTooltip"), this.repositoryName));
			WicketUtils.setInputPlaceholder(get("searchBox"), getString("gb.search"));
		}

		@Override
		public void onSubmit() {
			Constants.SearchType searchType = this.searchTypeModel.getObject();
			String searchString = this.searchBoxModel.getObject();
			if (StringUtils.isEmpty(searchString)) {
				// redirect to self to avoid wicket page update bug
				final String absoluteUrl = getCanonicalUrl();
				getRequestCycle().setRequestTarget(new RedirectRequestTarget(absoluteUrl));
				return;
			}
			for (final Constants.SearchType type : Constants.SearchType.values()) {
				if (searchString.toLowerCase().startsWith(type.name().toLowerCase() + ":")) {
					searchType = type;
					searchString = searchString.substring(type.name().toLowerCase().length() + 1)
							.trim();
					break;
				}
			}
			Class<? extends BasePage> searchPageClass = GitSearchPage.class;
			final RepositoryModel model = app().repositories().getRepositoryModel(
					this.repositoryName);
			if (app().settings().getBoolean(Keys.web.allowLuceneIndexing, true)
					&& !ArrayUtils.isEmpty(model.indexedBranches)) {
				// this repository is Lucene-indexed
				searchPageClass = LuceneSearchPage.class;
			}
			// use an absolute url to workaround Wicket-Tomcat problems with
			// mounted url parameters (issue-111)
			final PageParameters params = WicketUtils.newSearchParameter(this.repositoryName, null,
					searchString, searchType);
			final String absoluteUrl = getCanonicalUrl(searchPageClass, params);
			getRequestCycle().setRequestTarget(new RedirectRequestTarget(absoluteUrl));
		}
	}
}
