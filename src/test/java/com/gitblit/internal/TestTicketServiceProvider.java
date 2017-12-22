package com.gitblit.internal;

import com.gitblit.tickets.ITicketService;
import com.gitblit.tickets.NullTicketService;
import com.google.inject.Provider;

public final class TestTicketServiceProvider implements
		Provider<ITicketService> {

	private static TestTicketServiceProvider instance = new TestTicketServiceProvider();

	@Override
	public ITicketService get() {
		// TODO Auto-generated method stub
		return new NullTicketService(TestRuntimeManager.getInstance(),
				TestPluginManager.getInstance(), null, null,
				TestRepositoryManager.getInstance());
	}

	public static TestTicketServiceProvider getInstance() {
		return instance;
	}
}
