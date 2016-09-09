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
package com.gitblit.tests;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.bouncycastle.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.models.Mailing;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Attachment;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.Field;
import com.gitblit.models.TicketModel.Patchset;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.TicketModel.Type;
import com.gitblit.tests.mock.MemorySettings;
import com.gitblit.tickets.ITicketService;
import com.gitblit.tickets.ITicketService.TicketFilter;
import com.gitblit.tickets.QueryResult;
import com.gitblit.tickets.TicketIndexer.Lucene;
import com.gitblit.tickets.TicketLabel;
import com.gitblit.tickets.TicketMilestone;
import com.gitblit.tickets.TicketNotifier;
import com.gitblit.utils.JGitUtils;

/**
 * Tests the mechanics of Gitblit ticket management.
 *
 * @author James Moger
 *
 */
public abstract class TicketServiceTest extends GitblitUnitTest {

	private ITicketService service;

	protected abstract RepositoryModel getRepository();

	protected abstract ITicketService getService(boolean deleteAll) throws Exception;

	protected IStoredSettings getSettings(boolean deleteAll) throws Exception {
		final File dir = new File(GitBlitSuite.REPOSITORIES, getRepository().name);
		if (deleteAll) {
			FileUtils.deleteDirectory(dir);
			JGitUtils.createRepository(GitBlitSuite.REPOSITORIES, getRepository().name).close();
		}

		final File luceneDir = new File(dir, "tickets/lucene");
		luceneDir.mkdirs();

		final Map<String, Object> map = new HashMap<String, Object>();
		map.put(Keys.git.repositoriesFolder, GitBlitSuite.REPOSITORIES.getAbsolutePath());
		map.put(Keys.tickets.indexFolder, luceneDir.getAbsolutePath());

		final IStoredSettings settings = new MemorySettings(map);
		return settings;
	}

	@Before
	public void setup() throws Exception {
		this.service = getService(true);
	}

	@After
	public void cleanup() {
		this.service.stop();
	}

	@Test
	public void testLifecycle() throws Exception {
		// query non-existent ticket
		final TicketModel nonExistent = this.service.getTicket(getRepository(), 0);
		assertNull(nonExistent);

		// create and insert a ticket
		Change c1 = newChange("testCreation() " + Long.toHexString(System.currentTimeMillis()));
		TicketModel ticket = this.service.createTicket(getRepository(), c1);
		assertTrue(ticket.number > 0);

		// retrieve ticket and compare
		TicketModel constructed = this.service.getTicket(getRepository(), ticket.number);
		compare(ticket, constructed);

		assertEquals(1, constructed.changes.size());

		// C1: create the ticket
		int changeCount = 0;
		c1 = newChange("testUpdates() " + Long.toHexString(System.currentTimeMillis()));
		ticket = this.service.createTicket(getRepository(), c1);
		assertTrue(ticket.number > 0);
		changeCount++;

		constructed = this.service.getTicket(getRepository(), ticket.number);
		compare(ticket, constructed);
		assertEquals(1, constructed.changes.size());

		// C2: set owner
		final Change c2 = new Change("C2");
		c2.comment("I'll fix this");
		c2.setField(Field.responsible, c2.author);
		constructed = this.service.updateTicket(getRepository(), ticket.number, c2);
		assertNotNull(constructed);
		assertEquals(2, constructed.changes.size());
		assertEquals(c2.author, constructed.responsible);
		changeCount++;

		// C3: add a note
		final Change c3 = new Change("C3");
		c3.comment("yeah, this is working");
		constructed = this.service.updateTicket(getRepository(), ticket.number, c3);
		assertNotNull(constructed);
		assertEquals(3, constructed.changes.size());
		changeCount++;

		if (this.service.supportsAttachments()) {
			// C4: add attachment
			final Change c4 = new Change("C4");
			final Attachment a = newAttachment();
			c4.addAttachment(a);
			constructed = this.service.updateTicket(getRepository(), ticket.number, c4);
			assertNotNull(constructed);
			assertTrue(constructed.hasAttachments());
			final Attachment a1 = this.service
					.getAttachment(getRepository(), ticket.number, a.name);
			assertEquals(a.content.length, a1.content.length);
			assertTrue(Arrays.areEqual(a.content, a1.content));
			changeCount++;
		}

		// C5: close the issue
		final Change c5 = new Change("C5");
		c5.comment("closing issue");
		c5.setField(Field.status, Status.Resolved);
		constructed = this.service.updateTicket(getRepository(), ticket.number, c5);
		assertNotNull(constructed);
		changeCount++;
		assertTrue(constructed.isClosed());
		assertEquals(changeCount, constructed.changes.size());

		final List<TicketModel> allTickets = this.service.getTickets(getRepository());
		final List<TicketModel> openTickets = this.service.getTickets(getRepository(),
				new TicketFilter() {
					@Override
					public boolean accept(TicketModel ticket) {
						return ticket.isOpen();
					}
				});
		final List<TicketModel> closedTickets = this.service.getTickets(getRepository(),
				new TicketFilter() {
					@Override
					public boolean accept(TicketModel ticket) {
						return ticket.isClosed();
					}
				});
		assertTrue(allTickets.size() > 0);
		assertEquals(1, openTickets.size());
		assertEquals(1, closedTickets.size());

		// build a new Lucene index
		this.service.reindex(getRepository());
		List<QueryResult> hits = this.service.searchFor(getRepository(), "working", 1, 10);
		assertEquals(1, hits.size());

		// reindex a ticket
		ticket = allTickets.get(0);
		final Change change = new Change("reindex");
		change.comment("this is a test of reindexing a ticket");
		this.service.updateTicket(getRepository(), ticket.number, change);
		ticket = this.service.getTicket(getRepository(), ticket.number);

		hits = this.service.searchFor(getRepository(), "reindexing", 1, 10);
		assertEquals(1, hits.size());

		this.service.stop();
		this.service = getService(false);

		// Lucene field query
		List<QueryResult> results = this.service.queryFor(Lucene.status.matches(Status.New.name()),
				1, 10, Lucene.created.name(), true);
		assertEquals(1, results.size());
		assertTrue(results.get(0).title.startsWith("testCreation"));

		// Lucene field query
		results = this.service.queryFor(Lucene.status.matches(Status.Resolved.name()), 1, 10,
				Lucene.created.name(), true);
		assertEquals(1, results.size());
		assertTrue(results.get(0).title.startsWith("testUpdates"));

		// check the ids
		assertEquals("[1, 2]", this.service.getIds(getRepository()).toString());

		// delete all tickets
		for (final TicketModel aTicket : allTickets) {
			assertTrue(this.service.deleteTicket(getRepository(), aTicket.number, "D"));
		}
	}

	@Test
	public void testChangeComment() {
		// C1: create the ticket
		final Change c1 = newChange("testChangeComment() "
				+ Long.toHexString(System.currentTimeMillis()));
		TicketModel ticket = this.service.createTicket(getRepository(), c1);
		assertTrue(ticket.number > 0);
		assertTrue(ticket.changes.get(0).hasComment());

		ticket = this.service.updateComment(ticket, c1.comment.id, "E1", "I changed the comment");
		assertNotNull(ticket);
		assertTrue(ticket.changes.get(0).hasComment());
		assertEquals("I changed the comment", ticket.changes.get(0).comment.text);

		assertTrue(this.service.deleteTicket(getRepository(), ticket.number, "D"));
	}

	@Test
	public void testDeleteComment() {
		// C1: create the ticket
		final Change c1 = newChange("testDeleteComment() "
				+ Long.toHexString(System.currentTimeMillis()));
		TicketModel ticket = this.service.createTicket(getRepository(), c1);
		assertTrue(ticket.number > 0);
		assertTrue(ticket.changes.get(0).hasComment());

		ticket = this.service.deleteComment(ticket, c1.comment.id, "D1");
		assertNotNull(ticket);
		assertEquals(1, ticket.changes.size());
		assertFalse(ticket.changes.get(0).hasComment());

		assertTrue(this.service.deleteTicket(getRepository(), ticket.number, "D"));
	}

	@Test
	public void testMilestones() {
		this.service.createMilestone(getRepository(), "M1", "james");
		this.service.createMilestone(getRepository(), "M2", "frank");
		this.service.createMilestone(getRepository(), "M3", "joe");

		List<TicketMilestone> milestones = this.service.getMilestones(getRepository(), Status.Open);
		assertEquals("Unexpected open milestones count", 3, milestones.size());

		for (final TicketMilestone milestone : milestones) {
			milestone.status = Status.Resolved;
			milestone.due = new Date();
			assertTrue("failed to update milestone " + milestone.name,
					this.service.updateMilestone(getRepository(), milestone, "ted"));
		}

		milestones = this.service.getMilestones(getRepository(), Status.Open);
		assertEquals("Unexpected open milestones count", 0, milestones.size());

		milestones = this.service.getMilestones(getRepository(), Status.Resolved);
		assertEquals("Unexpected resolved milestones count", 3, milestones.size());

		for (final TicketMilestone milestone : milestones) {
			assertTrue("failed to delete milestone " + milestone.name,
					this.service.deleteMilestone(getRepository(), milestone.name, "lucifer"));
		}
	}

	@Test
	public void testLabels() {
		this.service.createLabel(getRepository(), "L1", "james");
		this.service.createLabel(getRepository(), "L2", "frank");
		this.service.createLabel(getRepository(), "L3", "joe");

		List<TicketLabel> labels = this.service.getLabels(getRepository());
		assertEquals("Unexpected open labels count", 3, labels.size());

		for (final TicketLabel label : labels) {
			label.color = "#ffff00";
			assertTrue("failed to update label " + label.name,
					this.service.updateLabel(getRepository(), label, "ted"));
		}

		labels = this.service.getLabels(getRepository());
		assertEquals("Unexpected labels count", 3, labels.size());

		for (final TicketLabel label : labels) {
			assertTrue("failed to delete label " + label.name,
					this.service.deleteLabel(getRepository(), label.name, "lucifer"));
		}
	}

	@Test
	public void testPriorityAndSeverity() {
		// C1: create and insert a ticket
		final Change c1 = newChange("testPriorityAndSeverity() "
				+ Long.toHexString(System.currentTimeMillis()));
		final TicketModel ticket = this.service.createTicket(getRepository(), c1);
		assertTrue(ticket.number > 0);
		assertEquals(TicketModel.Priority.Normal, ticket.priority);
		assertEquals(TicketModel.Severity.Unrated, ticket.severity);

		TicketModel constructed = this.service.getTicket(getRepository(), ticket.number);
		compare(ticket, constructed);

		// C2: Change Priority max
		final Change c2 = new Change("C2");
		c2.setField(Field.priority, TicketModel.Priority.Urgent);
		constructed = this.service.updateTicket(getRepository(), ticket.number, c2);
		assertNotNull(constructed);
		assertEquals(2, constructed.changes.size());
		assertEquals(TicketModel.Priority.Urgent, constructed.priority);
		assertEquals(TicketModel.Severity.Unrated, constructed.severity);

		// C3: Change Severity max
		final Change c3 = new Change("C3");
		c3.setField(Field.severity, TicketModel.Severity.Catastrophic);
		constructed = this.service.updateTicket(getRepository(), ticket.number, c3);
		assertNotNull(constructed);
		assertEquals(3, constructed.changes.size());
		assertEquals(TicketModel.Priority.Urgent, constructed.priority);
		assertEquals(TicketModel.Severity.Catastrophic, constructed.severity);

		// C4: Change Priority min
		final Change c4 = new Change("C3");
		c4.setField(Field.priority, TicketModel.Priority.Low);
		constructed = this.service.updateTicket(getRepository(), ticket.number, c4);
		assertNotNull(constructed);
		assertEquals(4, constructed.changes.size());
		assertEquals(TicketModel.Priority.Low, constructed.priority);
		assertEquals(TicketModel.Severity.Catastrophic, constructed.severity);
	}

	private static Change newChange(String summary) {
		final Change change = new Change("C1");
		change.setField(Field.title, summary);
		change.setField(Field.body, "this is my description");
		change.setField(Field.labels, "helpdesk");
		change.comment("my comment");
		return change;
	}

	private static Attachment newAttachment() {
		final Attachment attachment = new Attachment("test1.txt");
		attachment.content = new byte[] { 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
				0x4a };
		return attachment;
	}

	private static void compare(TicketModel ticket, TicketModel constructed) {
		assertEquals(ticket.number, constructed.number);
		assertEquals(ticket.createdBy, constructed.createdBy);
		assertEquals(ticket.responsible, constructed.responsible);
		assertEquals(ticket.title, constructed.title);
		assertEquals(ticket.body, constructed.body);
		assertEquals(ticket.created, constructed.created);

		assertTrue(ticket.hasLabel("helpdesk"));
	}

	@Test
	public void testNotifier() {
		final Change kernel = new Change("james");
		kernel.setField(Field.title, "Sample ticket");
		kernel.setField(Field.body,
				"this **is** my sample body\n\n- I hope\n- you really\n- *really* like it");
		kernel.setField(Field.status, Status.New);
		kernel.setField(Field.type, Type.Proposal);

		kernel.comment("this is a sample comment on a kernel change");

		final Patchset patchset = new Patchset();
		patchset.insertions = 100;
		patchset.deletions = 10;
		patchset.number = 1;
		patchset.rev = 25;
		patchset.tip = "50f57913f816d04a16b7407134de5d8406421f37";
		kernel.patchset = patchset;

		TicketModel ticket = this.service.createTicket(getRepository(), 0L, kernel);

		final Change merge = new Change("james");
		merge.setField(Field.mergeSha, patchset.tip);
		merge.setField(Field.mergeTo, "master");
		merge.setField(Field.status, Status.Merged);

		ticket = this.service.updateTicket(getRepository(), ticket.number, merge);
		ticket.repository = getRepository().name;

		final TicketNotifier notifier = this.service.createNotifier();
		final Mailing mailing = notifier.queueMailing(ticket);
		assertNotNull(mailing);
	}
}