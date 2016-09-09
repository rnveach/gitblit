/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright 2013 gitblit.com.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.servlet;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revplot.AbstractPlotRenderer;
import org.eclipse.jgit.revplot.PlotCommit;
import org.eclipse.jgit.revplot.PlotCommitList;
import org.eclipse.jgit.revplot.PlotLane;
import org.eclipse.jgit.revplot.PlotWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Handles requests for branch graphs
 *
 * @author James Moger
 *
 */
@Singleton
public class BranchGraphServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final int LANE_WIDTH = 14;

	// must match tr.commit css height
	private static final int ROW_HEIGHT = 24;

	private static final int RIGHT_PAD = 2;

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Stroke[] strokeCache;

	private final IStoredSettings settings;

	private final IRepositoryManager repositoryManager;

	@Inject
	public BranchGraphServlet(IStoredSettings settings, IRepositoryManager repositoryManager) {

		this.settings = settings;
		this.repositoryManager = repositoryManager;

		this.strokeCache = new Stroke[4];
		for (int i = 1; i < this.strokeCache.length; i++) {
			this.strokeCache[i] = new BasicStroke(i);
		}
	}

	/**
	 * Returns an url to this servlet for the specified parameters.
	 *
	 * @param baseURL
	 * @param repository
	 * @param objectId
	 * @param numberCommits
	 * @return an url
	 */
	public static String asLink(String baseURL, String repository, String objectId,
			int numberCommits) {
		if ((baseURL.length() > 0) && (baseURL.charAt(baseURL.length() - 1) == '/')) {
			baseURL = baseURL.substring(0, baseURL.length() - 1);
		}
		return baseURL + Constants.BRANCH_GRAPH_PATH + "?r=" + repository
				+ (objectId == null ? "" : ("&h=" + objectId))
				+ (numberCommits > 0 ? ("&l=" + numberCommits) : "");
	}

	@Override
	protected long getLastModified(HttpServletRequest req) {
		final String repository = req.getParameter("r");
		if (StringUtils.isEmpty(repository)) {
			return 0;
		}
		String objectId = req.getParameter("h");
		Repository r = null;
		try {
			r = this.repositoryManager.getRepository(repository);
			if (StringUtils.isEmpty(objectId)) {
				objectId = JGitUtils.getHEADRef(r);
			}
			final ObjectId id = r.resolve(objectId);
			if (id == null) {
				return 0;
			}
			final RevCommit commit = JGitUtils.getCommit(r, objectId);
			return JGitUtils.getCommitDate(commit).getTime();
		}
		catch (final Exception e) {
			this.log.error("Failed to determine last modified", e);
			return 0;
		}
		finally {
			if (r != null) {
				r.close();
			}
		}
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		Repository r = null;
		PlotWalk rw = null;
		try {
			final String repository = request.getParameter("r");
			if (StringUtils.isEmpty(repository)) {
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				response.getWriter().append("Bad request");
				return;
			}
			String objectId = request.getParameter("h");
			final String length = request.getParameter("l");

			r = this.repositoryManager.getRepository(repository);
			if (r == null) {
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				response.getWriter().append("Bad request");
				return;
			}

			rw = new PlotWalk(r);
			if (StringUtils.isEmpty(objectId)) {
				objectId = JGitUtils.getHEADRef(r);
			}

			final ObjectId id = r.resolve(objectId);
			if (id == null) {
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				response.getWriter().append("Bad request");
				return;
			}
			rw.markStart(rw.lookupCommit(id));

			// default to the items-per-page setting, unless specified
			final int maxCommits = this.settings.getInteger(Keys.web.itemsPerPage, 50);
			int requestedCommits = maxCommits;
			if (!StringUtils.isEmpty(length)) {
				final int l = Integer.parseInt(length);
				if (l > 0) {
					requestedCommits = l;
				}
			}

			// fetch the requested commits plus some extra so that the last
			// commit displayed *likely* has correct lane assignments
			final CommitList commitList = new CommitList();
			commitList.source(rw);
			commitList.fillTo(2 * Math.max(requestedCommits, maxCommits));

			// determine the appropriate width for the image
			int numLanes = 1;
			final int numCommits = Math.min(requestedCommits, commitList.size());
			if (numCommits > 1) {
				// determine graph width
				final Set<String> parents = new TreeSet<String>();
				for (int i = 0; i < commitList.size(); i++) {
					final PlotCommit<Lane> commit = commitList.get(i);
					boolean checkLane = false;

					if (i < numCommits) {
						// commit in visible list
						checkLane = true;

						// remember parents
						for (final RevCommit p : commit.getParents()) {
							parents.add(p.getName());
						}
					} else if (parents.contains(commit.getName())) {
						// commit outside visible list, but it is a parent of a
						// commit in the visible list so we need to know it's
						// lane
						// assignment
						checkLane = true;
					}

					if (checkLane) {
						final int pos = commit.getLane().getPosition();
						numLanes = Math.max(numLanes, pos + 1);
					}
				}
			}

			final int graphWidth = (numLanes * LANE_WIDTH) + RIGHT_PAD;
			final int rowHeight = ROW_HEIGHT;

			// create an image buffer and render the lanes
			BufferedImage image = new BufferedImage(graphWidth, rowHeight * numCommits,
					BufferedImage.TYPE_INT_ARGB);

			Graphics2D g = null;
			try {
				g = image.createGraphics();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
						RenderingHints.VALUE_ANTIALIAS_ON);
				final LanesRenderer renderer = new LanesRenderer();
				for (int i = 0; i < commitList.size(); i++) {
					final PlotCommit<Lane> commit = commitList.get(i);
					Graphics row = g.create(0, i * rowHeight, graphWidth, rowHeight);
					try {
						renderer.paint(row, commit, rowHeight, graphWidth);
					}
					finally {
						row.dispose();
						row = null;
					}
				}
			}
			finally {
				if (g != null) {
					g.dispose();
					g = null;
				}
			}

			// write the image buffer to the client
			response.setContentType("image/png");
			if (numCommits > 1) {
				response.setHeader("Cache-Control", "public, max-age=60, must-revalidate");
				response.setDateHeader("Last-Modified", JGitUtils.getCommitDate(commitList.get(0))
						.getTime());
			}
			final OutputStream os = response.getOutputStream();
			ImageIO.write(image, "png", os);
			os.flush();
			image.flush();
			image = null;
		}
		catch (final Exception e) {
			e.printStackTrace();
		}
		finally {
			if (rw != null) {
				rw.dispose();
				rw = null;
			}
			if (r != null) {
				r.close();
				r = null;
			}
		}
	}

	private Stroke stroke(final int width) {
		if (width < this.strokeCache.length) {
			return this.strokeCache[width];
		}
		return new BasicStroke(width);
	}

	static class CommitList extends PlotCommitList<Lane> {
		final List<Color> laneColors;
		final LinkedList<Color> colors;

		CommitList() {
			this.laneColors = new ArrayList<Color>();
			this.laneColors.add(new Color(133, 166, 214));
			this.laneColors.add(new Color(221, 205, 93));
			this.laneColors.add(new Color(199, 134, 57));
			this.laneColors.add(new Color(131, 150, 98));
			this.laneColors.add(new Color(197, 123, 127));
			this.laneColors.add(new Color(139, 136, 140));
			this.laneColors.add(new Color(48, 135, 144));
			this.laneColors.add(new Color(190, 93, 66));
			this.laneColors.add(new Color(143, 163, 54));
			this.laneColors.add(new Color(180, 148, 74));
			this.laneColors.add(new Color(101, 101, 217));
			this.laneColors.add(new Color(72, 153, 119));
			this.laneColors.add(new Color(23, 101, 160));
			this.laneColors.add(new Color(132, 164, 118));
			this.laneColors.add(new Color(255, 230, 59));
			this.laneColors.add(new Color(136, 176, 70));
			this.laneColors.add(new Color(255, 138, 1));
			this.laneColors.add(new Color(123, 187, 95));
			this.laneColors.add(new Color(233, 88, 98));
			this.laneColors.add(new Color(93, 158, 254));
			this.laneColors.add(new Color(175, 215, 0));
			this.laneColors.add(new Color(140, 134, 142));
			this.laneColors.add(new Color(232, 168, 21));
			this.laneColors.add(new Color(0, 172, 191));
			this.laneColors.add(new Color(251, 58, 4));
			this.laneColors.add(new Color(63, 64, 255));
			this.laneColors.add(new Color(27, 194, 130));
			this.laneColors.add(new Color(0, 104, 183));

			this.colors = new LinkedList<Color>();
			repackColors();
		}

		private void repackColors() {
			this.colors.addAll(this.laneColors);
		}

		@Override
		protected Lane createLane() {
			final Lane lane = new Lane();
			if (this.colors.isEmpty()) {
				repackColors();
			}
			lane.color = this.colors.removeFirst();
			return lane;
		}

		@Override
		protected void recycleLane(final Lane lane) {
			this.colors.add(lane.color);
		}
	}

	static class Lane extends PlotLane {

		private static final long serialVersionUID = 1L;

		Color color;

		@Override
		public boolean equals(Object o) {
			return super.equals(o) && this.color.equals(((Lane) o).color);
		}

		@Override
		public int hashCode() {
			return super.hashCode() ^ this.color.hashCode();
		}
	}

	class LanesRenderer extends AbstractPlotRenderer<Lane, Color> implements Serializable {

		private static final long serialVersionUID = 1L;

		final Color commitDotFill = new Color(220, 220, 220);

		final Color commitDotOutline = new Color(110, 110, 110);

		transient Graphics2D g;

		void paint(Graphics in, PlotCommit<Lane> commit, int h, int w) {
			this.g = (Graphics2D) in.create();
			try {
				if (commit != null) {
					paintCommit(commit, h);
				}
			}
			finally {
				this.g.dispose();
				this.g = null;
			}
		}

		@Override
		protected void drawLine(Color color, int x1, int y1, int x2, int y2, int width) {
			if (y1 == y2) {
				x1 -= width / 2;
				x2 -= width / 2;
			} else if (x1 == x2) {
				y1 -= width / 2;
				y2 -= width / 2;
			}

			this.g.setColor(color);
			this.g.setStroke(stroke(width));
			this.g.drawLine(x1, y1, x2, y2);
		}

		@Override
		protected void drawCommitDot(int x, int y, int w, int h) {
			this.g.setColor(this.commitDotFill);
			this.g.setStroke(BranchGraphServlet.this.strokeCache[2]);
			this.g.fillOval(x + 2, y + 1, w - 2, h - 2);
			this.g.setColor(this.commitDotOutline);
			this.g.drawOval(x + 2, y + 1, w - 2, h - 2);
		}

		@Override
		protected void drawBoundaryDot(int x, int y, int w, int h) {
			drawCommitDot(x, y, w, h);
		}

		@Override
		protected void drawText(String msg, int x, int y) {
		}

		@Override
		protected Color laneColor(Lane myLane) {
			return myLane != null ? myLane.color : Color.black;
		}

		@Override
		protected int drawLabel(int x, int y, Ref ref) {
			return 0;
		}
	}
}
