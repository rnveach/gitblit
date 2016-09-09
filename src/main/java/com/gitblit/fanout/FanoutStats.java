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
package com.gitblit.fanout;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.Date;

/**
 * Encapsulates the runtime stats of a fanout service.
 *
 * @author James Moger
 *
 */
public class FanoutStats implements Serializable {

	private static final long serialVersionUID = 1L;

	public long concurrentConnectionLimit;
	public boolean allowAllChannelAnnouncements;
	public boolean strictRequestTermination;

	public Date bootDate;
	public long rejectedConnectionCount;
	public int peakConnectionCount;
	public long currentChannels;
	public long currentSubscriptions;
	public long currentConnections;
	public long totalConnections;
	public long totalAnnouncements;
	public long totalMessages;
	public long totalSubscribes;
	public long totalUnsubscribes;
	public long totalPings;

	public String info() {
		int i = 0;
		final StringBuilder sb = new StringBuilder();
		sb.append(infoStr(i++, "boot date"));
		sb.append(infoStr(i++, "strict request termination"));
		sb.append(infoStr(i++, "allow connection \"all\" announcements"));
		sb.append(infoInt(i++, "concurrent connection limit"));
		sb.append(infoInt(i++, "concurrent limit rejected connections"));
		sb.append(infoInt(i++, "peak connections"));
		sb.append(infoInt(i++, "current connections"));
		sb.append(infoInt(i++, "current channels"));
		sb.append(infoInt(i++, "current subscriptions"));
		sb.append(infoInt(i++, "user-requested subscriptions"));
		sb.append(infoInt(i++, "total connections"));
		sb.append(infoInt(i++, "total announcements"));
		sb.append(infoInt(i++, "total messages"));
		sb.append(infoInt(i++, "total subscribes"));
		sb.append(infoInt(i++, "total unsubscribes"));
		sb.append(infoInt(i++, "total pings"));
		final String template = sb.toString();

		final String info = MessageFormat.format(template, this.bootDate.toString(), Boolean
				.toString(this.strictRequestTermination), Boolean
				.toString(this.allowAllChannelAnnouncements), this.concurrentConnectionLimit,
				this.rejectedConnectionCount, this.peakConnectionCount, this.currentConnections,
				this.currentChannels, this.currentSubscriptions, this.currentSubscriptions == 0 ? 0
						: (this.currentSubscriptions - this.currentConnections),
				this.totalConnections, this.totalAnnouncements, this.totalMessages,
				this.totalSubscribes, this.totalUnsubscribes, this.totalPings);
		return info;
	}

	private static String infoStr(int index, String label) {
		return label + ": {" + index + "}\n";
	}

	private static String infoInt(int index, String label) {
		return label + ": {" + index + ",number,0}\n";
	}

}
