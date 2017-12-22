package com.gitblit.wicket.pages;

import org.apache.wicket.util.tester.WicketTester;
import org.junit.Test;

import com.gitblit.internal.AbstractTest;
import com.gitblit.internal.TestAuthenticationManager;
import com.gitblit.internal.TestPluginManager;
import com.gitblit.internal.TestRepositoryManager;
import com.gitblit.internal.TestRuntimeManager;
import com.gitblit.internal.TestTicketServiceProvider;
import com.gitblit.wicket.GitBlitWebApp;

public class ActivityPageTest extends AbstractTest {
	@Test
	public void test() {
		WicketTester tester = new WicketTester(new GitBlitWebApp(null,
				new TestTicketServiceProvider(),
				TestRuntimeManager.getInstance(),
				TestPluginManager.getInstance(), null, null,
				TestAuthenticationManager.getInstance(),
				TestRepositoryManager.getInstance(), null, null, null, null,
				null));
		tester.startPage(ActivityPage.class);
		tester.assertRenderedPage(ActivityPage.class);
	}
}
