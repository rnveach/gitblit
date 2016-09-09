package com.gitblit.models;

import java.io.Serializable;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.WebPage;

import com.gitblit.utils.StringUtils;

public class Menu {

	/**
	 * A MenuItem for a drop down menu.
	 *
	 * @author James Moger
	 * @since 1.6.0
	 */
	public abstract static class MenuItem implements Serializable {

		private static final long serialVersionUID = 1L;

		final String displayText;

		MenuItem(String displayText) {
			this.displayText = displayText;
		}

		@Override
		public int hashCode() {
			return this.displayText.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof MenuItem) {
				return hashCode() == o.hashCode();
			}
			return false;
		}

		@Override
		public String toString() {
			return this.displayText;
		}
	}

	/**
	 * A divider for the menu.
	 *
	 * @since 1.6.0
	 */
	public static class MenuDivider extends MenuItem {

		private static final long serialVersionUID = 1L;

		public MenuDivider() {
			super("");
		}
	}

	/**
	 * A MenuItem for setting a parameter of the current url.
	 *
	 * @author James Moger
	 *
	 */
	public static class ParameterMenuItem extends MenuItem {

		private static final long serialVersionUID = 1L;

		final PageParameters parameters;
		final String parameter;
		final String value;
		final boolean isSelected;

		/**
		 * @param displayText
		 */
		public ParameterMenuItem(String displayText) {
			this(displayText, null, null, null);
		}

		/**
		 * @param displayText
		 * @param parameter
		 * @param value
		 */
		public ParameterMenuItem(String displayText, String parameter, String value) {
			this(displayText, parameter, value, null);
		}

		/**
		 * @param displayText
		 * @param parameter
		 * @param value
		 */
		public ParameterMenuItem(String displayText, String parameter, String value,
				PageParameters params) {
			super(displayText);
			this.parameter = parameter;
			this.value = value;

			if (params == null) {
				// no parameters specified
				this.parameters = new PageParameters();
				setParameter(parameter, value);
				this.isSelected = false;
			} else {
				this.parameters = new PageParameters(params);
				if (this.parameters.containsKey(parameter)) {
					this.isSelected = params.getString(parameter).equals(value);
					// set the new selection value
					setParameter(parameter, value);
				} else {
					// not currently selected
					this.isSelected = false;
					setParameter(parameter, value);
				}
			}
		}

		protected void setParameter(String parameter, String value) {
			if (!StringUtils.isEmpty(parameter)) {
				if (StringUtils.isEmpty(value)) {
					this.parameters.remove(parameter);
				} else {
					this.parameters.put(parameter, value);
				}
			}
		}

		public String formatParameter() {
			if (StringUtils.isEmpty(this.parameter) || StringUtils.isEmpty(this.value)) {
				return "";
			}
			return this.parameter + "=" + this.value;
		}

		public PageParameters getPageParameters() {
			return this.parameters;
		}

		public boolean isSelected() {
			return this.isSelected;
		}

		@Override
		public int hashCode() {
			if (StringUtils.isEmpty(this.displayText)) {
				return this.value.hashCode() + this.parameter.hashCode();
			}
			return this.displayText.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof MenuItem) {
				return hashCode() == o.hashCode();
			}
			return false;
		}

		@Override
		public String toString() {
			if (StringUtils.isEmpty(this.displayText)) {
				return formatParameter();
			}
			return this.displayText;
		}
	}

	/**
	 * Menu item for toggling a parameter.
	 *
	 */
	public static class ToggleMenuItem extends ParameterMenuItem {

		private static final long serialVersionUID = 1L;

		/**
		 * @param displayText
		 * @param parameter
		 * @param value
		 */
		public ToggleMenuItem(String displayText, String parameter, String value,
				PageParameters params) {
			super(displayText, parameter, value, params);
			if (this.isSelected) {
				// already selected, so remove this enables toggling
				this.parameters.remove(parameter);
			}
		}
	}

	/**
	 * Menu item for linking to another Wicket page.
	 *
	 * @since 1.6.0
	 */
	public static class PageLinkMenuItem extends MenuItem {

		private static final long serialVersionUID = 1L;

		private final Class<? extends WebPage> pageClass;

		private final PageParameters params;

		/**
		 * Page Link Item links to another page.
		 *
		 * @param displayText
		 * @param pageClass
		 * @since 1.6.0
		 */
		public PageLinkMenuItem(String displayText, Class<? extends WebPage> pageClass) {
			this(displayText, pageClass, null);
		}

		/**
		 * Page Link Item links to another page.
		 *
		 * @param displayText
		 * @param pageClass
		 * @param params
		 * @since 1.6.0
		 */
		public PageLinkMenuItem(String displayText, Class<? extends WebPage> pageClass,
				PageParameters params) {
			super(displayText);
			this.pageClass = pageClass;
			this.params = params;
		}

		/**
		 * @return the page class
		 * @since 1.6.0
		 */
		public Class<? extends WebPage> getPageClass() {
			return this.pageClass;
		}

		/**
		 * @return the page parameters
		 * @since 1.6.0
		 */
		public PageParameters getPageParameters() {
			return this.params;
		}
	}

	/**
	 * Menu item to link to an external page.
	 *
	 * @since 1.6.0
	 */
	public static class ExternalLinkMenuItem extends MenuItem {

		private static final long serialVersionUID = 1L;

		private final String href;

		private final boolean newWindow;

		/**
		 * External Link Item links to something else.
		 *
		 * @param displayText
		 * @param href
		 * @since 1.6.0
		 */
		public ExternalLinkMenuItem(String displayText, String href) {
			this(displayText, href, false);
		}

		/**
		 * External Link Item links to something else.
		 *
		 * @param displayText
		 * @param href
		 * @since 1.6.0
		 */
		public ExternalLinkMenuItem(String displayText, String href, boolean newWindow) {
			super(displayText);
			this.href = href;
			this.newWindow = newWindow;
		}

		/**
		 * @since 1.6.0
		 */
		public String getHref() {
			return this.href;
		}

		/**
		 * @since 1.6.0
		 */
		public boolean openInNewWindow() {
			return this.newWindow;
		}
	}
}
