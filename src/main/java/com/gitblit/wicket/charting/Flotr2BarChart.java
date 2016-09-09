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
package com.gitblit.wicket.charting;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

/**
 * Implementation of a Bar chart using the flotr2 library
 *
 * @author Tim Ryan
 * 
 */
public class Flotr2BarChart extends Chart {

	private static final long serialVersionUID = 1L;
	boolean xAxisIsDate = false;

	public Flotr2BarChart(String tagId, String title, String keyName, String valueName) {
		super(tagId, title, keyName, valueName);

	}

	@Override
	protected void appendChart(StringBuilder sb) {

		final String dName = "data_" + this.dataName;
		sb.append("var labels_" + this.dataName + " = [");
		if (this.xAxisIsDate) {

			// Generate labels for the dates
			final SimpleDateFormat df = new SimpleDateFormat(this.dateFormat);
			df.setTimeZone(getTimeZone());

			// Sort the values first
			Collections.sort(this.values, new Comparator<ChartValue>() {

				@Override
				public int compare(ChartValue o1, ChartValue o2) {
					final long long1 = Long.parseLong(o1.name);
					final long long2 = Long.parseLong(o2.name);
					return (int) (long2 - long1);
				}

			});

			for (int i = 0; i < this.values.size(); i++) {
				final ChartValue value = this.values.get(i);
				final Date date = new Date(Long.parseLong(value.name));
				final String label = df.format(date);
				if (i > 0) {
					sb.append(",");
				}
				sb.append("[\"" + label + "\", " + value.name + "]");
			}

		} else {
			for (int i = 0; i < this.values.size(); i++) {
				final ChartValue value = this.values.get(i);
				if (i > 0) {
					sb.append(",");
				}
				sb.append("\"" + value.name + "\"");
			}
		}
		line(sb, "];");

		line(sb, MessageFormat.format("var {0} = Flotr.draw(document.getElementById(''{1}''),",
				dName, this.tagId));

		// Add the data
		line(sb, "[");
		line(sb, "{ data : [ ");
		for (int i = 0; i < this.values.size(); i++) {
			final ChartValue value = this.values.get(i);
			if (i > 0) {
				sb.append(",");
			}
			if (this.xAxisIsDate) {
				line(sb, MessageFormat.format("[{0}, {1}] ", value.name,
						Float.toString(value.value)));
			} else {
				line(sb,
						MessageFormat.format("[{0}, {1}] ", Integer.toString(i),
								Float.toString(value.value)));
			}

		}
		line(sb,
				MessageFormat.format(" ], label : \"{0}\", color: ''#FF9900'' '}'", this.valueName));
		line(sb, "]");

		// Add the options
		line(sb, ", {");
		if ((this.title != null) && (this.title.isEmpty() == false)) {
			line(sb, MessageFormat.format("title : \"{0}\",", this.title));
		}
		line(sb, "bars : {");
		line(sb, "  show : true,");
		line(sb, "  horizontal : false,");
		line(sb, "  barWidth : 1");
		line(sb, "},");
		line(sb, "points: { show: false },");
		line(sb, "mouse: {");
		line(sb, "  track: true,");
		line(sb, "  lineColor: '#002060',");
		line(sb, "  position: 'ne',");
		line(sb, "  trackFormatter: function (obj) {");
		line(sb, "    return labels_" + this.dataName
				+ "[obj.index] + ': ' + parseInt(obj.y) + ' ' +  obj.series.label;");
		line(sb, "  }");
		line(sb, "},");
		line(sb, "xaxis: {");
		line(sb, "  showLabels: true,");
		line(sb, "  showMinorLabels: false,");
		line(sb, "  tickFormatter: function (x) {");
		line(sb, "   var index = parseInt(x);");
		line(sb, "    if(x % 1 == 0 && labels_" + this.dataName + "[index])");
		line(sb, "      return labels_" + this.dataName + "[index];");
		line(sb, "    return \"\";");
		line(sb, "  },");
		line(sb, "  margin: 10");
		line(sb, "},");
		line(sb, "yaxis: {");
		line(sb, "  showLabels: false,");
		line(sb, "  showMinorLabels: false,");
		line(sb, "  tickDecimals: 0,");
		line(sb, "  margin: 10");
		line(sb, "},");
		line(sb, "grid: {");
		line(sb, "  verticalLines: false,");
		line(sb, "  minorVerticalLines: null,");
		line(sb, "  horizontalLines: true,");
		line(sb, "  minorHorizontalLines: null,");
		line(sb, "  outlineWidth: 1,");
		line(sb, "  outline: 's'");
		line(sb, "},");
		line(sb, "legend: {");
		line(sb, "  show: false");
		line(sb, "}");
		line(sb, "});");

	}

	@Override
	public void addValue(Date date, int value) {
		this.xAxisIsDate = true;
		final String name = String.valueOf(date.getTime());
		super.addValue(name, value);
	}

}
