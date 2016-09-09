package de.akquinet.devops;

public class ManualUITestLaunch {
	public static void main(String[] args) {
		final int httpPort = 8080, httpsPort = 8443, shutdownPort = 8081;
		final String gitblitPropertiesPath = "src/test/config/test-ui-gitblit.properties", usersPropertiesPath = "src/test/config/test-ui-users.conf";

		final GitblitRunnable gitblitRunnable = new GitblitRunnable(httpPort, httpsPort,
				shutdownPort, gitblitPropertiesPath, usersPropertiesPath);
		final Thread serverThread = new Thread(gitblitRunnable);
		serverThread.start();
	}
}
