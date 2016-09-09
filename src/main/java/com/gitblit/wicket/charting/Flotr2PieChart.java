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

import com.gitblit.utils.StringUtils;

/**
 * Implementation of a Pie chart using the flotr2 library
 *
 * @author Tim Ryan
 *
 */
public class Flotr2PieChart extends Chart {

	private static final long serialVersionUID = 1L;

	public Flotr2PieChart(String tagId, String title, String keyName, String valueName) {
		super(tagId, title, keyName, valueName);
	}

	@Override
	protected void appendChart(StringBuilder sb) {

		final String dName = "data_" + this.dataName;
		line(sb, "var selected_" + this.dataName + " = null;");
		line(sb, MessageFormat.format("var {0} = Flotr.draw(document.getElementById(''{1}''),",
				dName, this.tagId));

		// Add the data
		line(sb, "[");
		for (int i = 0; i < this.values.size(); i++) {
			final ChartValue value = this.values.get(i);
			if (i > 0) {
				sb.append(",");
			}
			line(sb, MessageFormat.format(
					"'{'data : [ [0, {0}] ], label : \"{1}\", color: ''{2}'' '}'",
					Float.toString(value.value), value.name, StringUtils.getColor(value.name)));
		}
		line(sb, "]");

		// Add the options
		line(sb, ", {");
		line(sb, MessageFormat.format("title : \"{0}\",", this.title));
		line(sb, "fontSize : 2,");
		line(sb, "pie : {");
		line(sb, "  show : true,");
		line(sb, "  labelFormatter : function (pie, slice) {");
		line(sb, "    if(slice / pie > .05)");
		line(sb, "      return Math.round(slice / pie * 100).toString() + \"%\";");
		line(sb, "  }");
		line(sb, "}");
		line(sb, ", mouse: {");
		line(sb, "  track: true,");
		line(sb, "  lineColor: '#002060',");
		line(sb, "  trackFormatter: function (obj)");
		line(sb, "  {");
		line(sb, "    selected_" + this.dataName + " = obj.series.label;");
		line(sb,
				"    return obj.series.label + \": \" + parseInt(obj.y) + \" (\" + Math.round(obj.fraction * 100) + \"%)\";");
		line(sb, "  }");
		line(sb, "}");
		line(sb, ", xaxis: {");
		line(sb, "  margin: false,");
		line(sb, "  showLabels: false,");
		line(sb, "  showMinorLabels: false");
		line(sb, "}");
		line(sb, ", yaxis: {");
		line(sb, "  margin: false,");
		line(sb, "  min: 20,");
		line(sb, "  showLabels: false,");
		line(sb, "  showMinorLabels: false");
		line(sb, "}");
		line(sb, ", grid: {");
		line(sb, "  verticalLines: false,");
		line(sb, "  minorVerticalLines: null,");
		line(sb, "  horizontalLines: false,");
		line(sb, "  minorHorizontalLines: null,");
		line(sb, "  outlineWidth: 0");
		line(sb, "}");
		line(sb, ", legend: {");
		if (this.showLegend) {
			line(sb, "  show: true");
		} else {
			line(sb, "  show: false");
		}
		line(sb, "}");
		line(sb, "});");

		if ((this.clickUrl != null) && (this.clickUrl.isEmpty() == false)) {
			line(sb,
					MessageFormat
							.format("Flotr.EventAdapter.observe(document.getElementById(''{0}''), ''flotr:click'', function (mouse, a, b, c) '{'",
									this.tagId));
			line(sb, "  window.location.href = \"" + this.clickUrl + "\" + selected_"
					+ this.dataName + ";");
			line(sb, "});");
		}

	}

}
