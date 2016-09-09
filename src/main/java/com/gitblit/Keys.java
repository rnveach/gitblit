package com.gitblit;

public final class Keys {

	public static final class execution {

		public static final String _ROOT = "execution";

		public static final String defaultThreadPoolSize = "execution.defaultThreadPoolSize";

	}

	public static final class fanout {

		public static final String _ROOT = "fanout";

		public static final String bindInterface = "fanout.bindInterface";

		public static final String connectionLimit = "fanout.connectionLimit";

		public static final String port = "fanout.port";

		public static final String useNio = "fanout.useNio";

	}

	public static final class federation {

		public static final String _ROOT = "federation";

		public static final String allowProposals = "federation.allowProposals";

		public static final String defaultFrequency = "federation.defaultFrequency";

		public static final String name = "federation.name";

		public static final String passphrase = "federation.passphrase";

		public static final String proposalsFolder = "federation.proposalsFolder";

		public static final String sets = "federation.sets";

	}

	public static final class filestore {

		public static final String _ROOT = "filestore";

		public static final String maxUploadSize = "filestore.maxUploadSize";

		public static final String storageFolder = "filestore.storageFolder";

	}

	public static final class git {

		public static final String _ROOT = "git";

		public static final String acceptedPushTransports = "git.acceptedPushTransports";

		public static final String allowAnonymousPushes = "git.allowAnonymousPushes";

		public static final String allowCreateOnPush = "git.allowCreateOnPush";

		public static final String cacheRepositoryList = "git.cacheRepositoryList";

		public static final String certificateUsernameOIDs = "git.certificateUsernameOIDs";

		public static final String checkReceivedObjects = "git.checkReceivedObjects";

		public static final String checkReferencedObjectsAreReachable = "git.checkReferencedObjectsAreReachable";

		public static final String createRepositoriesShared = "git.createRepositoriesShared";

		public static final String daemonBindInterface = "git.daemonBindInterface";

		public static final String daemonPort = "git.daemonPort";

		public static final String defaultAccessRestriction = "git.defaultAccessRestriction";

		public static final String defaultAuthorizationControl = "git.defaultAuthorizationControl";

		public static final String defaultGarbageCollectionPeriod = "git.defaultGarbageCollectionPeriod";

		public static final String defaultGarbageCollectionThreshold = "git.defaultGarbageCollectionThreshold";

		public static final String defaultIncrementalPushTagPrefix = "git.defaultIncrementalPushTagPrefix";

		public static final String deltaBaseCacheLimit = "git.deltaBaseCacheLimit";

		public static final String enableGarbageCollection = "git.enableGarbageCollection";

		public static final String enableGitServlet = "git.enableGitServlet";

		public static final String enableMirroring = "git.enableMirroring";

		public static final String enforceCertificateValidity = "git.enforceCertificateValidity";

		public static final String garbageCollectionHour = "git.garbageCollectionHour";

		public static final String gitignoreFolder = "git.gitignoreFolder";

		public static final String maxObjectSizeLimit = "git.maxObjectSizeLimit";

		public static final String maxPackSizeLimit = "git.maxPackSizeLimit";

		public static final String mirrorPeriod = "git.mirrorPeriod";

		public static final String onlyAccessBareRepositories = "git.onlyAccessBareRepositories";

		public static final String packedGitLimit = "git.packedGitLimit";

		public static final String packedGitMmap = "git.packedGitMmap";

		public static final String packedGitOpenFiles = "git.packedGitOpenFiles";

		public static final String packedGitWindowSize = "git.packedGitWindowSize";

		public static final String repositoriesFolder = "git.repositoriesFolder";

		public static final String requiresClientCertificate = "git.requiresClientCertificate";

		public static final String searchExclusions = "git.searchExclusions";

		public static final String searchRecursionDepth = "git.searchRecursionDepth";

		public static final String searchRepositoriesSubfolders = "git.searchRepositoriesSubfolders";

		public static final String sshAdvertisedHost = "git.sshAdvertisedHost";

		public static final String sshAdvertisedPort = "git.sshAdvertisedPort";

		public static final String sshBackend = "git.sshBackend";

		public static final String sshBindInterface = "git.sshBindInterface";

		public static final String sshCommandStartThreads = "git.sshCommandStartThreads";

		public static final String sshKeysFolder = "git.sshKeysFolder";

		public static final String sshAuthenticationMethods = "git.sshAuthenticationMethods";

		public static final String sshKeysManager = "git.sshKeysManager";

		public static final String sshKrb5Keytab = "git.sshKrb5Keytab";

		public static final String sshKrb5ServicePrincipalName = "git.sshKrb5ServicePrincipalName";

		public static final String sshKrb5StripDomain = "git.sshKrb5StripDomain";

		public static final String sshPort = "git.sshPort";

		public static final String sshWithKrb5 = "git.sshWithKrb5";

		public static final String submoduleUrlPatterns = "git.submoduleUrlPatterns";

		public static final String userRepositoryPrefix = "git.userRepositoryPrefix";

	}

	public static final class groovy {

		public static final String _ROOT = "groovy";

		public static final String customFields = "groovy.customFields";

		public static final String grapeFolder = "groovy.grapeFolder";

		public static final String postReceiveScripts = "groovy.postReceiveScripts";

		public static final String preReceiveScripts = "groovy.preReceiveScripts";

		public static final String scriptsFolder = "groovy.scriptsFolder";

	}

	public static final class mail {

		public static final String _ROOT = "mail";

		public static final String adminAddresses = "mail.adminAddresses";

		public static final String debug = "mail.debug";

		public static final String fromAddress = "mail.fromAddress";

		public static final String mailingLists = "mail.mailingLists";

		public static final String password = "mail.password";

		public static final String port = "mail.port";

		public static final String server = "mail.server";

		public static final String smtps = "mail.smtps";

		public static final String starttls = "mail.starttls";

		public static final String username = "mail.username";

	}

	public static final class plugins {

		public static final String _ROOT = "plugins";

		public static final String folder = "plugins.folder";

		public static final String httpProxyAuthorization = "plugins.httpProxyAuthorization";

		public static final String httpProxyHost = "plugins.httpProxyHost";

		public static final String httpProxyPort = "plugins.httpProxyPort";

		public static final String registry = "plugins.registry";

	}

	public static final class realm {

		public static final String _ROOT = "realm";

		public static final String authenticationProviders = "realm.authenticationProviders";

		public static final String minPasswordLength = "realm.minPasswordLength";

		public static final String passwordStorage = "realm.passwordStorage";

		public static final String userService = "realm.userService";

		public static final class container {

			public static final String _ROOT = "realm.container";

			public static final String autoCreateAccounts = "realm.container.autoCreateAccounts";

			public static final class autoAccounts {

				public static final String _ROOT = "realm.container.autoAccounts";

				public static final String adminRole = "realm.container.autoAccounts.adminRole";

				public static final String displayName = "realm.container.autoAccounts.displayName";

				public static final String emailAddress = "realm.container.autoAccounts.emailAddress";

				public static final String locale = "realm.container.autoAccounts.locale";

			}

		}

		public static final class htpasswd {

			public static final String _ROOT = "realm.htpasswd";

			public static final String userfile = "realm.htpasswd.userfile";

		}

		public static final class httpheader {

			public static final String _ROOT = "realm.httpheader";

			public static final String autoCreateAccounts = "realm.httpheader.autoCreateAccounts";

			public static final String teamheader = "realm.httpheader.teamheader";

			public static final String teamseparator = "realm.httpheader.teamseparator";

			public static final String userheader = "realm.httpheader.userheader";

		}

		public static final class ldap {

			public static final String _ROOT = "realm.ldap";

			public static final String accountBase = "realm.ldap.accountBase";

			public static final String accountPattern = "realm.ldap.accountPattern";

			public static final String admins = "realm.ldap.admins";

			public static final String bindpattern = "realm.ldap.bindpattern";

			public static final String displayName = "realm.ldap.displayName";

			public static final String email = "realm.ldap.email";

			public static final String groupBase = "realm.ldap.groupBase";

			public static final String groupEmptyMemberPattern = "realm.ldap.groupEmptyMemberPattern";

			public static final String groupMemberPattern = "realm.ldap.groupMemberPattern";

			public static final String maintainTeams = "realm.ldap.maintainTeams";

			public static final String password = "realm.ldap.password";

			public static final String removeDeletedUsers = "realm.ldap.removeDeletedUsers";

			public static final String server = "realm.ldap.server";

			public static final String syncPeriod = "realm.ldap.syncPeriod";

			public static final String synchronize = "realm.ldap.synchronize";

			public static final String uid = "realm.ldap.uid";

			public static final String sshPublicKey = "realm.ldap.sshPublicKey";

			public static final String username = "realm.ldap.username";

		}

		public static final class pam {

			public static final String _ROOT = "realm.pam";

			public static final String serviceName = "realm.pam.serviceName";

		}

		public static final class redmine {

			public static final String _ROOT = "realm.redmine";

			public static final String url = "realm.redmine.url";

		}

		public static final class salesforce {

			public static final String _ROOT = "realm.salesforce";

			public static final String orgId = "realm.salesforce.orgId";

		}

		public static final class windows {

			public static final String _ROOT = "realm.windows";

			public static final String allowGuests = "realm.windows.allowGuests";

			public static final String defaultDomain = "realm.windows.defaultDomain";

			public static final String permitBuiltInAdministrators = "realm.windows.permitBuiltInAdministrators";

		}

	}

	public static final class regex {

		public static final String _ROOT = "regex";

		public static final String global = "regex.global";

	}

	public static final class server {

		public static final String _ROOT = "server";

		public static final String certificateAlias = "server.certificateAlias";

		public static final String contextPath = "server.contextPath";

		public static final String httpBindInterface = "server.httpBindInterface";

		public static final String httpPort = "server.httpPort";

		public static final String httpsBindInterface = "server.httpsBindInterface";

		public static final String httpsPort = "server.httpsPort";

		public static final String redirectToHttpsPort = "server.redirectToHttpsPort";

		public static final String requireClientCertificates = "server.requireClientCertificates";

		public static final String shutdownPort = "server.shutdownPort";

		public static final String httpIdleTimeout = "server.httpIdleTimeout";

		public static final String storePassword = "server.storePassword";

		public static final String tempFolder = "server.tempFolder";

		public static final String threadPoolSize = "server.threadPoolSize";

	}

	public static final class tickets {

		public static final String _ROOT = "tickets";

		public static final String acceptNewPatchsets = "tickets.acceptNewPatchsets";

		public static final String acceptNewTickets = "tickets.acceptNewTickets";

		public static final String closeOnPushCommitMessageRegex = "tickets.closeOnPushCommitMessageRegex";

		public static final String indexFolder = "tickets.indexFolder";

		public static final String linkOnPushCommitMessageRegex = "tickets.linkOnPushCommitMessageRegex";

		public static final String perPage = "tickets.perPage";

		public static final String requireApproval = "tickets.requireApproval";

		public static final String mergeType = "tickets.mergeType";

		public static final String service = "tickets.service";

		public static final class redis {

			public static final String _ROOT = "tickets.redis";

			public static final String url = "tickets.redis.url";

		}

	}

	public static final class web {

		public static final String _ROOT = "web";

		public static final String activityCacheDays = "web.activityCacheDays";

		public static final String activityDuration = "web.activityDuration";

		public static final String activityDurationChoices = "web.activityDurationChoices";

		public static final String activityDurationMaximum = "web.activityDurationMaximum";

		public static final String advertiseAccessPermissionForOtherUrls = "web.advertiseAccessPermissionForOtherUrls";

		public static final String aggressiveHeapManagement = "web.aggressiveHeapManagement";

		public static final String allowAdministration = "web.allowAdministration";

		public static final String allowAppCloneLinks = "web.allowAppCloneLinks";

		public static final String allowCookieAuthentication = "web.allowCookieAuthentication";

		public static final String allowDeletingNonEmptyRepositories = "web.allowDeletingNonEmptyRepositories";

		public static final String allowFlashCopyToClipboard = "web.allowFlashCopyToClipboard";

		public static final String allowForking = "web.allowForking";

		public static final String allowGravatar = "web.allowGravatar";

		public static final String allowLuceneIndexing = "web.allowLuceneIndexing";

		public static final String allowZipDownloads = "web.allowZipDownloads";

		public static final String authenticateAdminPages = "web.authenticateAdminPages";

		public static final String authenticateViewPages = "web.authenticateViewPages";

		public static final String avatarClass = "web.avatarClass";

		public static final String binaryExtensions = "web.binaryExtensions";

		public static final String blobEncodings = "web.blobEncodings";

		public static final String canonicalUrl = "web.canonicalUrl";

		public static final String commitMessageRenderer = "web.commitMessageRenderer";

		public static final String compressedDownloads = "web.compressedDownloads";

		public static final String confluenceExtensions = "web.confluenceExtensions";

		public static final String customFilters = "web.customFilters";

		public static final String datestampLongFormat = "web.datestampLongFormat";

		public static final String datestampShortFormat = "web.datestampShortFormat";

		public static final String datetimestampLongFormat = "web.datetimestampLongFormat";

		public static final String debugMode = "web.debugMode";

		public static final String displayUserPanel = "web.displayUserPanel";

		public static final String documents = "web.documents";

		public static final String enableRpcAdministration = "web.enableRpcAdministration";

		public static final String enableRpcManagement = "web.enableRpcManagement";

		public static final String enableRpcServlet = "web.enableRpcServlet";

		public static final String enforceHttpBasicAuthentication = "web.enforceHttpBasicAuthentication";

		public static final String forceDefaultLocale = "web.forceDefaultLocale";

		public static final String forwardSlashCharacter = "web.forwardSlashCharacter";

		public static final String generateActivityGraph = "web.generateActivityGraph";

		public static final String headerBackgroundColor = "web.headerBackgroundColor";

		public static final String headerBorderColor = "web.headerBorderColor";

		public static final String headerBorderFocusColor = "web.headerBorderFocusColor";

		public static final String headerForegroundColor = "web.headerForegroundColor";

		public static final String headerHoverColor = "web.headerHoverColor";

		public static final String headerLogo = "web.headerLogo";

		public static final String hideHeader = "web.hideHeader";

		public static final String imageExtensions = "web.imageExtensions";

		public static final String includePersonalRepositories = "web.includePersonalRepositories";

		public static final String itemsPerPage = "web.itemsPerPage";

		public static final String loginMessage = "web.loginMessage";

		public static final String luceneFrequency = "web.luceneFrequency";

		public static final String luceneIgnoreExtensions = "web.luceneIgnoreExtensions";

		public static final String markdownExtensions = "web.markdownExtensions";

		public static final String maxActivityCommits = "web.maxActivityCommits";

		public static final String maxDiffLines = "web.maxDiffLines";

		public static final String maxDiffLinesPerFile = "web.maxDiffLinesPerFile";

		public static final String mediawikiExtensions = "web.mediawikiExtensions";

		public static final String metricAuthorExclusions = "web.metricAuthorExclusions";

		public static final String mountParameters = "web.mountParameters";

		public static final String otherUrls = "web.otherUrls";

		public static final String overviewReflogCount = "web.overviewReflogCount";

		public static final String pageCacheExpires = "web.pageCacheExpires";

		public static final String prettyPrintExtensions = "web.prettyPrintExtensions";

		public static final String projectsFile = "web.projectsFile";

		public static final String reflogChangesPerPage = "web.reflogChangesPerPage";

		public static final String repositoriesMessage = "web.repositoriesMessage";

		public static final String repositoryListSwatches = "web.repositoryListSwatches";

		public static final String repositoryListType = "web.repositoryListType";

		public static final String repositoryRootGroupName = "web.repositoryRootGroupName";

		public static final String rootLink = "web.rootLink";

		public static final String shortCommitIdLength = "web.shortCommitIdLength";

		public static final String showBranchGraph = "web.showBranchGraph";

		public static final String showEmailAddresses = "web.showEmailAddresses";

		public static final String showFederationRegistrations = "web.showFederationRegistrations";

		public static final String showGitDaemonUrls = "web.showGitDaemonUrls";

		public static final String showHttpServletUrls = "web.showHttpServletUrls";

		public static final String showRepositorySizes = "web.showRepositorySizes";

		public static final String showSearchTypeSelection = "web.showSearchTypeSelection";

		public static final String showSshDaemonUrls = "web.showSshDaemonUrls";

		public static final String siteName = "web.siteName";

		public static final String summaryCommitCount = "web.summaryCommitCount";

		public static final String summaryRefsCount = "web.summaryRefsCount";

		public static final String summaryShowReadme = "web.summaryShowReadme";

		public static final String syndicationEntries = "web.syndicationEntries";

		public static final String tabLength = "web.tabLength";

		public static final String textileExtensions = "web.textileExtensions";

		public static final String timeFormat = "web.timeFormat";

		public static final String timezone = "web.timezone";

		public static final String tracwikiExtensions = "web.tracwikiExtensions";

		public static final String twikiExtensions = "web.twikiExtensions";

		public static final String useClientTimezone = "web.useClientTimezone";

		public static final String useResponsiveLayout = "web.useResponsiveLayout";

		public static final class robots {

			public static final String _ROOT = "web.robots";

			public static final String txt = "web.robots.txt";

		}

	}

}
