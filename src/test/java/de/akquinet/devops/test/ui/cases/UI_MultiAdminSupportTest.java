/*
 * Copyright 2013 akquinet tech@spree GmbH
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
package de.akquinet.devops.test.ui.cases;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import de.akquinet.devops.test.ui.generic.AbstractUITest;
import de.akquinet.devops.test.ui.view.RepoEditView;
import de.akquinet.devops.test.ui.view.RepoListView;

/**
 * tests the multi admin per repo feature.
 * 
 * @author saheba
 * 
 */
public class UI_MultiAdminSupportTest extends AbstractUITest {

	String baseUrl = "https://localhost:8443";
	RepoListView view;
	RepoEditView editView;
	private static final String TEST_MULTI_ADMIN_SUPPORT_REPO_NAME = "testmultiadminsupport";
	private static final String TEST_MULTI_ADMIN_SUPPORT_REPO_PATH = "~repocreator/"
			+ TEST_MULTI_ADMIN_SUPPORT_REPO_NAME + ".git";
	private static final String TEST_MULTI_ADMIN_SUPPORT_REPO_PATH_WITHOUT_SUFFIX = "~repocreator/"
			+ TEST_MULTI_ADMIN_SUPPORT_REPO_NAME;

	@Before
	public void before() {
		System.out.println("IN BEFORE");
		this.view = new RepoListView(AbstractUITest.getDriver(), this.baseUrl);
		this.editView = new RepoEditView(AbstractUITest.getDriver());
		AbstractUITest.getDriver().navigate().to(this.baseUrl);
	}

	@Test
	public void test_MultiAdminSelectionInStandardRepo() {
		// login
		this.view.login("repocreator", "repocreator");

		// create new repo
		this.view.navigateToNewRepo(1);
		this.editView.changeName(TEST_MULTI_ADMIN_SUPPORT_REPO_PATH);
		Assert.assertTrue(this.editView.navigateToPermissionsTab());

		Assert.assertTrue(this.editView
				.changeAccessRestriction(RepoEditView.RESTRICTION_AUTHENTICATED_VCP));
		Assert.assertTrue(this.editView.changeAuthorizationControl(RepoEditView.AUTHCONTROL_RWALL));

		// with a second admin
		this.editView.addOwner("admin");
		Assert.assertTrue(this.editView.save());
		// user is automatically forwarded to repo list view
		Assert.assertTrue(this.view.isEmptyRepo(TEST_MULTI_ADMIN_SUPPORT_REPO_PATH));
		Assert.assertTrue(this.view.isEditableRepo(TEST_MULTI_ADMIN_SUPPORT_REPO_PATH));
		Assert.assertTrue(this.view
				.isDeletableRepo(TEST_MULTI_ADMIN_SUPPORT_REPO_PATH_WITHOUT_SUFFIX));
		// logout repocreator
		this.view.logout();

		// check with admin account if second admin has the same rights
		this.view.login("admin", "admin");
		Assert.assertTrue(this.view.isEmptyRepo(TEST_MULTI_ADMIN_SUPPORT_REPO_PATH));
		Assert.assertTrue(this.view.isEditableRepo(TEST_MULTI_ADMIN_SUPPORT_REPO_PATH));
		Assert.assertTrue(this.view
				.isDeletableRepo(TEST_MULTI_ADMIN_SUPPORT_REPO_PATH_WITHOUT_SUFFIX));
		// delete repo to reach state as before test execution
		this.view.navigateToDeleteRepo(TEST_MULTI_ADMIN_SUPPORT_REPO_PATH_WITHOUT_SUFFIX);
		this.view.acceptAlertDialog();
		this.view.logout();

		Assert.assertTrue(this.view.isLoginPartVisible());
	}

}
