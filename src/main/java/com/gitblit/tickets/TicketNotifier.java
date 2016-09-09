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
package com.gitblit.tickets;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.git.PatchsetCommand;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.models.Mailing;
import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.Field;
import com.gitblit.models.TicketModel.Patchset;
import com.gitblit.models.TicketModel.Review;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.DiffUtils;
import com.gitblit.utils.DiffUtils.DiffStat;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.MarkdownUtils;
import com.gitblit.utils.StringUtils;

/**
 * Formats and queues ticket/patch notifications for dispatch to the mail
 * executor upon completion of a push or a ticket update. Messages are created
 * as Markdown and then transformed to html.
 *
 * @author James Moger
 *
 */
public class TicketNotifier {

	protected final Map<Long, Mailing> queue = new TreeMap<Long, Mailing>();

	private final String SOFT_BRK = "\n";

	private final String HARD_BRK = "\n\n";

	private final String HR = "----\n\n";

	private final IStoredSettings settings;

	private final INotificationManager notificationManager;

	private final IUserManager userManager;

	private final IRepositoryManager repositoryManager;

	private final ITicketService ticketService;

	private final String addPattern = "<span style=\"color:darkgreen;\">+{0}</span>";
	private final String delPattern = "<span style=\"color:darkred;\">-{0}</span>";

	public TicketNotifier(IRuntimeManager runtimeManager, INotificationManager notificationManager,
			IUserManager userManager, IRepositoryManager repositoryManager,
			ITicketService ticketService) {

		this.settings = runtimeManager.getSettings();
		this.notificationManager = notificationManager;
		this.userManager = userManager;
		this.repositoryManager = repositoryManager;
		this.ticketService = ticketService;
	}

	public void sendAll() {
		for (final Mailing mail : this.queue.values()) {
			this.notificationManager.send(mail);
		}
	}

	public void sendMailing(TicketModel ticket) {
		queueMailing(ticket);
		sendAll();
	}

	/**
	 * Queues an update notification.
	 *
	 * @param ticket
	 * @return a notification object used for testing
	 */
	public Mailing queueMailing(TicketModel ticket) {
		try {
			// format notification message
			final String markdown = formatLastChange(ticket);

			final StringBuilder html = new StringBuilder();
			html.append("<head>");
			html.append(readStyle());
			html.append(readViewTicketAction(ticket));
			html.append("</head>");
			html.append("<body>");
			html.append(MarkdownUtils.transformGFM(this.settings, markdown, ticket.repository));
			html.append("</body>");

			final Mailing mailing = Mailing.newHtml();
			mailing.from = getUserModel(
					ticket.updatedBy == null ? ticket.createdBy : ticket.updatedBy)
					.getDisplayName();
			mailing.subject = getSubject(ticket);
			mailing.content = html.toString();
			mailing.id = "ticket." + ticket.number + "."
					+ StringUtils.getSHA1(ticket.repository + ticket.number);

			setRecipients(ticket, mailing);
			this.queue.put(ticket.number, mailing);

			return mailing;
		}
		catch (final Exception e) {
			Logger.getLogger(getClass()).error("failed to queue mailing for #" + ticket.number, e);
		}
		return null;
	}

	protected String getSubject(TicketModel ticket) {
		final Change lastChange = ticket.changes.get(ticket.changes.size() - 1);
		final boolean newTicket = lastChange.isStatusChange() && (ticket.changes.size() == 1);
		final String re = newTicket ? "" : "Re: ";
		final String subject = MessageFormat.format("{0}[{1}] {2} (#{3,number,0})", re,
				StringUtils.stripDotGit(ticket.repository), ticket.title, ticket.number);
		return subject;
	}

	protected String formatLastChange(TicketModel ticket) {
		final Change lastChange = ticket.changes.get(ticket.changes.size() - 1);
		final UserModel user = getUserModel(lastChange.author);

		// define the fields we do NOT want to see in an email notification
		final Set<TicketModel.Field> fieldExclusions = new HashSet<TicketModel.Field>();
		fieldExclusions.addAll(Arrays.asList(Field.watchers, Field.voters));

		final StringBuilder sb = new StringBuilder();
		final boolean newTicket = lastChange.isStatusChange()
				&& (Status.New == lastChange.getStatus());
		boolean isFastForward = true;
		List<RevCommit> commits = null;
		DiffStat diffstat = null;

		String pattern;
		if (lastChange.hasPatchset()) {
			// patchset uploaded
			final Patchset patchset = lastChange.patchset;
			String base = "";
			// determine the changed paths
			Repository repo = null;
			try {
				repo = this.repositoryManager.getRepository(ticket.repository);
				if (patchset.isFF() && (patchset.rev > 1)) {
					// fast-forward update, just show the new data
					isFastForward = true;
					final Patchset prev = ticket.getPatchset(patchset.number, patchset.rev - 1);
					base = prev.tip;
				} else {
					// proposal OR non-fast-forward update
					isFastForward = false;
					base = patchset.base;
				}

				diffstat = DiffUtils.getDiffStat(repo, base, patchset.tip);
				commits = JGitUtils.getRevLog(repo, base, patchset.tip);
			}
			catch (final Exception e) {
				Logger.getLogger(getClass()).error("failed to get changed paths", e);
			}
			finally {
				if (repo != null) {
					repo.close();
				}
			}

			final String compareUrl = this.ticketService.getCompareUrl(ticket, base, patchset.tip);

			if (newTicket) {
				// new proposal
				pattern = "**{0}** is proposing a change.";
				sb.append(MessageFormat.format(pattern, user.getDisplayName()));
				fieldExclusions.add(Field.status);
				fieldExclusions.add(Field.title);
				fieldExclusions.add(Field.body);
			} else {
				// describe the patchset
				if (patchset.isFF()) {
					pattern = "**{0}** added {1} {2} to patchset {3}.";
					sb.append(MessageFormat.format(pattern, user.getDisplayName(), patchset.added,
							patchset.added == 1 ? "commit" : "commits", patchset.number));
				} else {
					pattern = "**{0}** uploaded patchset {1}. *({2})*";
					sb.append(MessageFormat.format(pattern, user.getDisplayName(), patchset.number,
							patchset.type.toString().toUpperCase()));
				}
			}
			sb.append(this.HARD_BRK);

			sb.append(MessageFormat
					.format("{0} {1}, {2} {3}, <span style=\"color:darkgreen;\">+{4} insertions</span>, <span style=\"color:darkred;\">-{5} deletions</span> from {6}. [compare]({7})",
							commits.size(), commits.size() == 1 ? "commit" : "commits",
							diffstat.paths.size(), diffstat.paths.size() == 1 ? "file" : "files",
							diffstat.getInsertions(), diffstat.getDeletions(),
							isFastForward ? "previous revision" : "merge base", compareUrl));

			// note commit additions on a rebase,if any
			switch (lastChange.patchset.type) {
			case Rebase:
				if (lastChange.patchset.added > 0) {
					sb.append(this.SOFT_BRK);
					sb.append(MessageFormat.format("{0} {1} added.", lastChange.patchset.added,
							lastChange.patchset.added == 1 ? "commit" : "commits"));
				}
				break;
			default:
				break;
			}
			sb.append(this.HARD_BRK);
		} else if (lastChange.isStatusChange()) {
			if (newTicket) {
				fieldExclusions.add(Field.status);
				fieldExclusions.add(Field.title);
				fieldExclusions.add(Field.body);
				pattern = "**{0}** created this ticket.";
				sb.append(MessageFormat.format(pattern, user.getDisplayName()));
			} else if (lastChange.hasField(Field.mergeSha)) {
				// closed by merged
				pattern = "**{0}** closed this ticket by merging {1} to {2}.";

				// identify patchset that closed the ticket
				String merged = ticket.mergeSha;
				for (final Patchset patchset : ticket.getPatchsets()) {
					if (patchset.tip.equals(ticket.mergeSha)) {
						merged = patchset.toString();
						break;
					}
				}
				sb.append(MessageFormat.format(pattern, user.getDisplayName(), merged,
						ticket.mergeTo));
			} else {
				// workflow status change by user
				pattern = "**{0}** changed the status of this ticket to **{1}**.";
				sb.append(MessageFormat.format(pattern, user.getDisplayName(), lastChange
						.getStatus().toString().toUpperCase()));
			}
			sb.append(this.HARD_BRK);
		} else if (lastChange.hasReview()) {
			// review
			final Review review = lastChange.review;
			pattern = "**{0}** has reviewed patchset {1,number,0} revision {2,number,0}.";
			sb.append(MessageFormat.format(pattern, user.getDisplayName(), review.patchset,
					review.rev));
			sb.append(this.HARD_BRK);

			final String d = this.settings.getString(Keys.web.datestampShortFormat, "yyyy-MM-dd");
			final String t = this.settings.getString(Keys.web.timeFormat, "HH:mm");
			final DateFormat df = new SimpleDateFormat(d + " " + t);
			final List<Change> reviews = ticket.getReviews(ticket.getPatchset(review.patchset,
					review.rev));
			sb.append("| Date | Reviewer      | Score | Description  |\n");
			sb.append("| :--- | :------------ | :---: | :----------- |\n");
			for (final Change change : reviews) {
				String name = change.author;
				final UserModel u = this.userManager.getUserModel(change.author);
				if (u != null) {
					name = u.getDisplayName();
				}
				String score;
				switch (change.review.score) {
				case approved:
					score = MessageFormat.format(this.addPattern, change.review.score.getValue());
					break;
				case vetoed:
					score = MessageFormat.format(this.delPattern,
							Math.abs(change.review.score.getValue()));
					break;
				default:
					score = "" + change.review.score.getValue();
				}
				final String date = df.format(change.date);
				sb.append(String.format("| %1$s | %2$s | %3$s | %4$s |\n", date, name, score,
						change.review.score.toString()));
			}
			sb.append(this.HARD_BRK);
		} else if (lastChange.hasComment()) {
			// comment update
			sb.append(MessageFormat.format("**{0}** commented on this ticket.",
					user.getDisplayName()));
			sb.append(this.HARD_BRK);
		} else if (lastChange.hasReference()) {
			// reference update
			String type = "?";

			switch (lastChange.reference.getSourceType()) {
			case Commit: {
				type = "commit";
			}
				break;
			case Ticket: {
				type = "ticket";
			}
				break;
			default: {
			}
				break;
			}

			sb.append(MessageFormat.format("**{0}** referenced this ticket in {1} {2}", type,
					lastChange.toString()));
			sb.append(this.HARD_BRK);

		} else {
			// general update
			pattern = "**{0}** has updated this ticket.";
			sb.append(MessageFormat.format(pattern, user.getDisplayName()));
			sb.append(this.HARD_BRK);
		}

		// ticket link
		sb.append(MessageFormat.format("[view ticket {0,number,0}]({1})", ticket.number,
				this.ticketService.getTicketUrl(ticket)));
		sb.append(this.HARD_BRK);

		if (newTicket) {
			// ticket title
			sb.append(MessageFormat.format("### {0}", ticket.title));
			sb.append(this.HARD_BRK);

			// ticket description, on state change
			if (StringUtils.isEmpty(ticket.body)) {
				sb.append("<span style=\"color: #888;\">no description entered</span>");
			} else {
				sb.append(ticket.body);
			}
			sb.append(this.HARD_BRK);
			sb.append(this.HR);
		}

		// field changes
		if (lastChange.hasFieldChanges()) {
			final Map<Field, String> filtered = new HashMap<Field, String>();
			for (final Map.Entry<Field, String> fc : lastChange.fields.entrySet()) {
				if (!fieldExclusions.contains(fc.getKey())) {
					// field is included
					filtered.put(fc.getKey(), fc.getValue());
				}
			}

			// sort by field ordinal
			final List<Field> fields = new ArrayList<Field>(filtered.keySet());
			Collections.sort(fields);

			if (filtered.size() > 0) {
				sb.append(this.HARD_BRK);
				sb.append("| Field Changes               ||\n");
				sb.append("| ------------: | :----------- |\n");
				for (final Field field : fields) {
					String value;
					if (filtered.get(field) == null) {
						value = "";
					} else {
						value = filtered.get(field).replace("\r\n", "<br/>").replace("\n", "<br/>")
								.replace("|", "&#124;");
					}
					sb.append(String.format("| **%1$s:** | %2$s |\n", field.name(), value));
				}
				sb.append(this.HARD_BRK);
			}
		}

		// new comment
		if (lastChange.hasComment()) {
			sb.append(this.HR);
			sb.append(lastChange.comment.text);
			sb.append(this.HARD_BRK);
		}

		// insert the patchset details and review instructions
		if (lastChange.hasPatchset() && ticket.isOpen()) {
			if ((commits != null) && (commits.size() > 0)) {
				// append the commit list
				final String title = isFastForward ? "Commits added to previous patchset revision"
						: "All commits in patchset";
				sb.append(MessageFormat.format("| {0} |||\n", title));
				sb.append("| SHA | Author | Title |\n");
				sb.append("| :-- | :----- | :---- |\n");
				for (final RevCommit commit : commits) {
					sb.append(MessageFormat.format("| {0} | {1} | {2} |\n", commit.getName(),
							commit.getAuthorIdent().getName(),
							StringUtils
									.trimString(commit.getShortMessage(), Constants.LEN_SHORTLOG)
									.replace("|", "&#124;")));
				}
				sb.append(this.HARD_BRK);
			}

			if (diffstat != null) {
				// append the changed path list
				final String title = isFastForward ? "Files changed since previous patchset revision"
						: "All files changed in patchset";
				sb.append(MessageFormat.format("| {0} |||\n", title));
				sb.append("| :-- | :----------- | :-: |\n");
				for (final PathChangeModel path : diffstat.paths) {
					final String add = MessageFormat.format(this.addPattern, path.insertions);
					final String del = MessageFormat.format(this.delPattern, path.deletions);
					String diff = null;
					switch (path.changeType) {
					case ADD:
						diff = add;
						break;
					case DELETE:
						diff = del;
						break;
					case MODIFY:
						if ((path.insertions > 0) && (path.deletions > 0)) {
							// insertions & deletions
							diff = add + "/" + del;
						} else if (path.insertions > 0) {
							// just insertions
							diff = add;
						} else {
							// just deletions
							diff = del;
						}
						break;
					default:
						diff = path.changeType.name();
						break;
					}
					sb.append(MessageFormat.format("| {0} | {1} | {2} |\n",
							getChangeType(path.changeType), path.name, diff));
				}
				sb.append(this.HARD_BRK);
			}

			sb.append(formatPatchsetInstructions(ticket, lastChange.patchset));
		}

		return sb.toString();
	}

	protected String getChangeType(ChangeType type) {
		String style = null;
		switch (type) {
		case ADD:
			style = "color:darkgreen;";
			break;
		case COPY:
			style = "";
			break;
		case DELETE:
			style = "color:darkred;";
			break;
		case MODIFY:
			style = "";
			break;
		case RENAME:
			style = "";
			break;
		default:
			break;
		}
		final String code = type.name().toUpperCase().substring(0, 1);
		if (style == null) {
			return code;
		} else {
			return MessageFormat
					.format("<strong><span style=\"{0}padding:2px;margin:2px;border:1px solid #ddd;\">{1}</span></strong>",
							style, code);
		}
	}

	/**
	 * Generates patchset review instructions for command-line git
	 *
	 * @param patchset
	 * @return instructions
	 */
	protected String formatPatchsetInstructions(TicketModel ticket, Patchset patchset) {
		final String canonicalUrl = this.settings.getString(Keys.web.canonicalUrl,
				"https://localhost:8443");
		final String repositoryUrl = canonicalUrl + Constants.R_PATH + ticket.repository;

		final String ticketBranch = Repository.shortenRefName(PatchsetCommand
				.getTicketBranch(ticket.number));
		final String patchsetBranch = PatchsetCommand.getPatchsetBranch(ticket.number,
				patchset.number);
		final String reviewBranch = PatchsetCommand.getReviewBranch(ticket.number);

		String instructions = readResource("commands.md");
		instructions = instructions.replace("${ticketId}", "" + ticket.number);
		instructions = instructions.replace("${patchset}", "" + patchset.number);
		instructions = instructions.replace("${repositoryUrl}", repositoryUrl);
		instructions = instructions.replace("${ticketRef}", ticketBranch);
		instructions = instructions.replace("${patchsetRef}", patchsetBranch);
		instructions = instructions.replace("${reviewBranch}", reviewBranch);
		instructions = instructions.replace("${ticketBranch}", ticketBranch);

		return instructions;
	}

	/**
	 * Gets the usermodel for the username. Creates a temp model, if required.
	 *
	 * @param username
	 * @return a usermodel
	 */
	protected UserModel getUserModel(String username) {
		UserModel user = this.userManager.getUserModel(username);
		if (user == null) {
			// create a temporary user model (for unit tests)
			user = new UserModel(username);
		}
		return user;
	}

	/**
	 * Set the proper recipients for a ticket.
	 *
	 * @param ticket
	 * @param mailing
	 */
	protected void setRecipients(TicketModel ticket, Mailing mailing) {
		final RepositoryModel repository = this.repositoryManager
				.getRepositoryModel(ticket.repository);

		//
		// Direct TO recipients
		// reporter & responsible
		//
		final Set<String> tos = new TreeSet<String>();
		tos.add(ticket.createdBy);
		if (!StringUtils.isEmpty(ticket.responsible)) {
			tos.add(ticket.responsible);
		}

		final Set<String> toAddresses = new TreeSet<String>();
		for (final String name : tos) {
			final UserModel user = this.userManager.getUserModel(name);
			if ((user != null) && !user.disabled) {
				if (!StringUtils.isEmpty(user.emailAddress)) {
					if (user.canView(repository)) {
						toAddresses.add(user.emailAddress);
					} else {
						LoggerFactory
								.getLogger(getClass())
								.warn(MessageFormat
										.format("ticket {0}-{1,number,0}: {2} can not receive notification",
												repository.name, ticket.number, user.username));
					}
				}
			}
		}

		//
		// CC recipients
		//
		final Set<String> ccs = new TreeSet<String>();

		// repository owners
		if (!ArrayUtils.isEmpty(repository.owners)) {
			ccs.addAll(repository.owners);
		}

		// cc users mentioned in last comment
		final Change lastChange = ticket.changes.get(ticket.changes.size() - 1);
		if (lastChange.hasComment()) {
			final Pattern p = Pattern.compile("\\s@([A-Za-z0-9-_]+)");
			final Matcher m = p.matcher(lastChange.comment.text);
			while (m.find()) {
				final String username = m.group();
				ccs.add(username);
			}
		}

		// cc users who are watching the ticket
		ccs.addAll(ticket.getWatchers());

		// TODO cc users who are watching the repository

		final Set<String> ccAddresses = new TreeSet<String>();
		for (final String name : ccs) {
			final UserModel user = this.userManager.getUserModel(name);
			if ((user != null) && !user.disabled) {
				if (!StringUtils.isEmpty(user.emailAddress)) {
					if (user.canView(repository)) {
						ccAddresses.add(user.emailAddress);
					} else {
						LoggerFactory
								.getLogger(getClass())
								.warn(MessageFormat
										.format("ticket {0}-{1,number,0}: {2} can not receive notification",
												repository.name, ticket.number, user.username));
					}
				}
			}
		}

		// cc repository mailing list addresses
		if (!ArrayUtils.isEmpty(repository.mailingLists)) {
			ccAddresses.addAll(repository.mailingLists);
		}
		ccAddresses.addAll(this.settings.getStrings(Keys.mail.mailingLists));

		// respect the author's email preference
		final UserModel lastAuthor = this.userManager.getUserModel(lastChange.author);
		if ((lastAuthor != null) && !lastAuthor.getPreferences().isEmailMeOnMyTicketChanges()) {
			toAddresses.remove(lastAuthor.emailAddress);
			ccAddresses.remove(lastAuthor.emailAddress);
		}

		mailing.setRecipients(toAddresses);
		mailing.setCCs(ccAddresses);
	}

	protected String readStyle() {
		final StringBuilder sb = new StringBuilder();
		sb.append("<style>\n");
		sb.append(readResource("email.css"));
		sb.append("</style>\n");
		return sb.toString();
	}

	protected String readViewTicketAction(TicketModel ticket) {
		String action = readResource("viewTicket.html");
		action = action.replace("${url}", this.ticketService.getTicketUrl(ticket));
		return action;
	}

	protected String readResource(String resource) {
		final StringBuilder sb = new StringBuilder();
		InputStream is = null;
		try {
			is = getClass().getResourceAsStream(resource);
			final List<String> lines = IOUtils.readLines(is);
			for (final String line : lines) {
				sb.append(line).append('\n');
			}
		}
		catch (final IOException e) {

		}
		finally {
			if (is != null) {
				try {
					is.close();
				}
				catch (final IOException e) {
				}
			}
		}
		return sb.toString();
	}
}
