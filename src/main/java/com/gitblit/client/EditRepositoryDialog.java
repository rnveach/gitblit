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
package com.gitblit.client;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;

import org.eclipse.jgit.lib.Repository;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.Constants.FederationStrategy;
import com.gitblit.Constants.RegistrantType;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;

/**
 * Dialog to create/edit a repository.
 *
 * @author James Moger
 */
public class EditRepositoryDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final String repositoryName;

	private final RepositoryModel repository;

	private boolean isCreate;

	private boolean canceled = true;

	private JTextField nameField;

	private JTextField descriptionField;

	private JCheckBox acceptNewPatchsets;

	private JCheckBox acceptNewTickets;

	private JCheckBox requireApproval;

	private JComboBox<String> mergeToField;

	private JCheckBox useIncrementalPushTags;

	private JCheckBox showRemoteBranches;

	private JCheckBox skipSizeCalculation;

	private JCheckBox skipSummaryMetrics;

	private JCheckBox isFrozen;

	private JTextField mailingListsField;

	private JComboBox<AccessRestrictionType> accessRestriction;

	private JRadioButton allowAuthenticated;

	private JRadioButton allowNamed;

	private JCheckBox allowForks;

	private JCheckBox verifyCommitter;

	private JComboBox<FederationStrategy> federationStrategy;

	private JPalette<String> ownersPalette;

	private JComboBox<String> headRefField;

	private JComboBox<Integer> gcPeriod;

	private JTextField gcThreshold;

	private JComboBox<Integer> maxActivityCommits;

	private RegistrantPermissionsPanel usersPalette;

	private JPalette<String> setsPalette;

	private RegistrantPermissionsPanel teamsPalette;

	private JPalette<String> indexedBranchesPalette;

	private JPalette<String> preReceivePalette;

	private JLabel preReceiveInherited;

	private JPalette<String> postReceivePalette;

	private JLabel postReceiveInherited;

	private final Set<String> repositoryNames;

	private JPanel customFieldsPanel;

	private List<JTextField> customTextfields;

	public EditRepositoryDialog(int protocolVersion) {
		this(protocolVersion, new RepositoryModel());
		this.isCreate = true;
		setTitle(Translation.get("gb.newRepository"));
	}

	public EditRepositoryDialog(int protocolVersion, RepositoryModel aRepository) {
		super();
		this.repositoryName = aRepository.name;
		this.repository = new RepositoryModel();
		this.repositoryNames = new HashSet<String>();
		this.isCreate = false;
		initialize(protocolVersion, aRepository);
		setModal(true);
		setResizable(false);
		setTitle(Translation.get("gb.edit") + ": " + aRepository.name);
		setIconImage(new ImageIcon(getClass().getResource("/gitblt-favicon.png")).getImage());
	}

	@Override
	protected JRootPane createRootPane() {
		final KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
		final JRootPane rootPane = new JRootPane();
		rootPane.registerKeyboardAction(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				setVisible(false);
			}
		}, stroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
		return rootPane;
	}

	private void initialize(int protocolVersion, RepositoryModel anRepository) {
		this.nameField = new JTextField(anRepository.name == null ? "" : anRepository.name, 35);
		this.descriptionField = new JTextField(anRepository.description == null ? ""
				: anRepository.description, 35);

		final JTextField originField = new JTextField(anRepository.origin == null ? ""
				: anRepository.origin, 40);
		originField.setEditable(false);

		if (ArrayUtils.isEmpty(anRepository.availableRefs)) {
			this.headRefField = new JComboBox<String>();
			this.headRefField.setEnabled(false);
		} else {
			this.headRefField = new JComboBox<String>(
					anRepository.availableRefs.toArray(new String[0]));
			this.headRefField.setSelectedItem(anRepository.HEAD);
		}

		final Integer[] gcPeriods = { 1, 2, 3, 4, 5, 7, 10, 14 };
		this.gcPeriod = new JComboBox<Integer>(gcPeriods);
		this.gcPeriod.setSelectedItem(anRepository.gcPeriod);

		this.gcThreshold = new JTextField(8);
		this.gcThreshold.setText(anRepository.gcThreshold);

		this.ownersPalette = new JPalette<String>(true);

		this.acceptNewTickets = new JCheckBox(Translation.get("gb.acceptsNewTicketsDescription"),
				anRepository.acceptNewTickets);
		this.acceptNewPatchsets = new JCheckBox(
				Translation.get("gb.acceptsNewPatchsetsDescription"),
				anRepository.acceptNewPatchsets);
		this.requireApproval = new JCheckBox(Translation.get("gb.requireApprovalDescription"),
				anRepository.requireApproval);

		if (ArrayUtils.isEmpty(anRepository.availableRefs)) {
			this.mergeToField = new JComboBox<String>();
			this.mergeToField.setEnabled(false);
		} else {
			this.mergeToField = new JComboBox<String>(
					anRepository.availableRefs.toArray(new String[0]));
			this.mergeToField.setSelectedItem(anRepository.mergeTo);
		}

		this.useIncrementalPushTags = new JCheckBox(
				Translation.get("gb.useIncrementalPushTagsDescription"),
				anRepository.useIncrementalPushTags);
		this.showRemoteBranches = new JCheckBox(
				Translation.get("gb.showRemoteBranchesDescription"),
				anRepository.showRemoteBranches);
		this.skipSizeCalculation = new JCheckBox(
				Translation.get("gb.skipSizeCalculationDescription"),
				anRepository.skipSizeCalculation);
		this.skipSummaryMetrics = new JCheckBox(
				Translation.get("gb.skipSummaryMetricsDescription"),
				anRepository.skipSummaryMetrics);
		this.isFrozen = new JCheckBox(Translation.get("gb.isFrozenDescription"),
				anRepository.isFrozen);

		this.maxActivityCommits = new JComboBox<Integer>(new Integer[] { -1, 0, 25, 50, 75, 100,
				150, 250, 500 });
		this.maxActivityCommits.setSelectedItem(anRepository.maxActivityCommits);

		this.mailingListsField = new JTextField(ArrayUtils.isEmpty(anRepository.mailingLists) ? ""
				: StringUtils.flattenStrings(anRepository.mailingLists, " "), 50);

		this.accessRestriction = new JComboBox<AccessRestrictionType>(
				AccessRestrictionType.values());
		this.accessRestriction.setRenderer(new AccessRestrictionRenderer());
		this.accessRestriction.setSelectedItem(anRepository.accessRestriction);
		this.accessRestriction.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					final AccessRestrictionType art = (AccessRestrictionType) EditRepositoryDialog.this.accessRestriction
							.getSelectedItem();
					EditRepositoryDialog.this.setupAccessPermissions(art);
				}
			}
		});

		final boolean authenticated = (anRepository.authorizationControl != null)
				&& AuthorizationControl.AUTHENTICATED.equals(anRepository.authorizationControl);
		this.allowAuthenticated = new JRadioButton(
				Translation.get("gb.allowAuthenticatedDescription"));
		this.allowAuthenticated.setSelected(authenticated);
		this.allowAuthenticated.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					EditRepositoryDialog.this.usersPalette.setEnabled(false);
					EditRepositoryDialog.this.teamsPalette.setEnabled(false);
				}
			}
		});

		this.allowNamed = new JRadioButton(Translation.get("gb.allowNamedDescription"));
		this.allowNamed.setSelected(!authenticated);
		this.allowNamed.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					EditRepositoryDialog.this.usersPalette.setEnabled(true);
					EditRepositoryDialog.this.teamsPalette.setEnabled(true);
				}
			}
		});

		final ButtonGroup group = new ButtonGroup();
		group.add(this.allowAuthenticated);
		group.add(this.allowNamed);

		final JPanel authorizationPanel = new JPanel(new GridLayout(0, 1));
		authorizationPanel.add(this.allowAuthenticated);
		authorizationPanel.add(this.allowNamed);

		this.allowForks = new JCheckBox(Translation.get("gb.allowForksDescription"),
				anRepository.allowForks);
		this.verifyCommitter = new JCheckBox(Translation.get("gb.verifyCommitterDescription"),
				anRepository.verifyCommitter);

		// federation strategies - remove ORIGIN choice if this repository has
		// no origin.
		final List<FederationStrategy> federationStrategies = new ArrayList<FederationStrategy>(
				Arrays.asList(FederationStrategy.values()));
		if (StringUtils.isEmpty(anRepository.origin)) {
			federationStrategies.remove(FederationStrategy.FEDERATE_ORIGIN);
		}
		this.federationStrategy = new JComboBox<FederationStrategy>(
				federationStrategies.toArray(new FederationStrategy[0]));
		this.federationStrategy.setRenderer(new FederationStrategyRenderer<FederationStrategy>());
		this.federationStrategy.setSelectedItem(anRepository.federationStrategy);

		final JPanel fieldsPanel = new JPanel(new GridLayout(0, 1));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.name"), this.nameField));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.description"), this.descriptionField));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.origin"), originField));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.headRef"), this.headRefField));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.gcPeriod"), this.gcPeriod));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.gcThreshold"), this.gcThreshold));

		fieldsPanel.add(newFieldPanel(Translation.get("gb.acceptsNewTickets"),
				this.acceptNewTickets));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.acceptsNewPatchsets"),
				this.acceptNewPatchsets));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.requireApproval"), this.requireApproval));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.mergeTo"), this.mergeToField));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.enableIncrementalPushTags"),
				this.useIncrementalPushTags));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.showRemoteBranches"),
				this.showRemoteBranches));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.skipSizeCalculation"),
				this.skipSizeCalculation));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.skipSummaryMetrics"),
				this.skipSummaryMetrics));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.maxActivityCommits"),
				this.maxActivityCommits));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.mailingLists"), this.mailingListsField));

		final JPanel clonePushPanel = new JPanel(new GridLayout(0, 1));
		clonePushPanel.add(newFieldPanel(Translation.get("gb.isFrozen"), this.isFrozen));
		clonePushPanel.add(newFieldPanel(Translation.get("gb.allowForks"), this.allowForks));
		clonePushPanel.add(newFieldPanel(Translation.get("gb.verifyCommitter"),
				this.verifyCommitter));

		this.usersPalette = new RegistrantPermissionsPanel(RegistrantType.USER);

		final JPanel northFieldsPanel = new JPanel(new BorderLayout(0, 5));
		northFieldsPanel.add(newFieldPanel(Translation.get("gb.owners"), this.ownersPalette),
				BorderLayout.NORTH);
		northFieldsPanel.add(
				newFieldPanel(Translation.get("gb.accessRestriction"), this.accessRestriction),
				BorderLayout.CENTER);

		final JPanel northAccessPanel = new JPanel(new BorderLayout(5, 5));
		northAccessPanel.add(northFieldsPanel, BorderLayout.NORTH);
		northAccessPanel.add(
				newFieldPanel(Translation.get("gb.authorizationControl"), authorizationPanel),
				BorderLayout.CENTER);
		northAccessPanel.add(clonePushPanel, BorderLayout.SOUTH);

		final JPanel accessPanel = new JPanel(new BorderLayout(5, 5));
		accessPanel.add(northAccessPanel, BorderLayout.NORTH);
		accessPanel.add(newFieldPanel(Translation.get("gb.userPermissions"), this.usersPalette),
				BorderLayout.CENTER);

		this.teamsPalette = new RegistrantPermissionsPanel(RegistrantType.TEAM);
		final JPanel teamsPanel = new JPanel(new BorderLayout(5, 5));
		teamsPanel.add(newFieldPanel(Translation.get("gb.teamPermissions"), this.teamsPalette),
				BorderLayout.CENTER);

		this.setsPalette = new JPalette<String>();
		final JPanel federationPanel = new JPanel(new BorderLayout(5, 5));
		federationPanel.add(
				newFieldPanel(Translation.get("gb.federationStrategy"), this.federationStrategy),
				BorderLayout.NORTH);
		federationPanel.add(newFieldPanel(Translation.get("gb.federationSets"), this.setsPalette),
				BorderLayout.CENTER);

		this.indexedBranchesPalette = new JPalette<String>();
		final JPanel indexedBranchesPanel = new JPanel(new BorderLayout(5, 5));
		indexedBranchesPanel.add(
				newFieldPanel(Translation.get("gb.indexedBranches"), this.indexedBranchesPalette),
				BorderLayout.CENTER);

		this.preReceivePalette = new JPalette<String>(true);
		this.preReceiveInherited = new JLabel();
		final JPanel preReceivePanel = new JPanel(new BorderLayout(5, 5));
		preReceivePanel.add(this.preReceivePalette, BorderLayout.CENTER);
		preReceivePanel.add(this.preReceiveInherited, BorderLayout.WEST);

		this.postReceivePalette = new JPalette<String>(true);
		this.postReceiveInherited = new JLabel();
		final JPanel postReceivePanel = new JPanel(new BorderLayout(5, 5));
		postReceivePanel.add(this.postReceivePalette, BorderLayout.CENTER);
		postReceivePanel.add(this.postReceiveInherited, BorderLayout.WEST);

		this.customFieldsPanel = new JPanel();
		this.customFieldsPanel.setLayout(new BoxLayout(this.customFieldsPanel, BoxLayout.Y_AXIS));
		final JScrollPane customFieldsScrollPane = new JScrollPane(this.customFieldsPanel);
		customFieldsScrollPane
				.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		customFieldsScrollPane
				.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

		final JTabbedPane panel = new JTabbedPane(SwingConstants.TOP);
		panel.addTab(Translation.get("gb.general"), fieldsPanel);
		panel.addTab(Translation.get("gb.accessRestriction"), accessPanel);
		if (protocolVersion >= 2) {
			panel.addTab(Translation.get("gb.teams"), teamsPanel);
		}
		panel.addTab(Translation.get("gb.federation"), federationPanel);
		if (protocolVersion >= 3) {
			panel.addTab(Translation.get("gb.indexedBranches"), indexedBranchesPanel);
		}
		panel.addTab(Translation.get("gb.preReceiveScripts"), preReceivePanel);
		panel.addTab(Translation.get("gb.postReceiveScripts"), postReceivePanel);

		panel.addTab(Translation.get("gb.customFields"), customFieldsScrollPane);

		setupAccessPermissions(anRepository.accessRestriction);

		final JButton createButton = new JButton(Translation.get("gb.save"));
		createButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				if (validateFields()) {
					EditRepositoryDialog.this.canceled = false;
					setVisible(false);
				}
			}
		});

		final JButton cancelButton = new JButton(Translation.get("gb.cancel"));
		cancelButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				EditRepositoryDialog.this.canceled = true;
				setVisible(false);
			}
		});

		final JPanel controls = new JPanel();
		controls.add(cancelButton);
		controls.add(createButton);

		final Insets _insets = new Insets(5, 5, 5, 5);
		final JPanel centerPanel = new JPanel(new BorderLayout(5, 5)) {

			private static final long serialVersionUID = 1L;

			@Override
			public Insets getInsets() {
				return _insets;
			}
		};
		centerPanel.add(panel, BorderLayout.CENTER);
		centerPanel.add(controls, BorderLayout.SOUTH);

		getContentPane().setLayout(new BorderLayout(5, 5));
		getContentPane().add(centerPanel, BorderLayout.CENTER);
		pack();
		this.nameField.requestFocus();
	}

	private static JPanel newFieldPanel(String label, JComponent comp) {
		return newFieldPanel(label, 150, comp);
	}

	private static JPanel newFieldPanel(String label, int labelSize, JComponent comp) {
		final JLabel fieldLabel = new JLabel(label);
		fieldLabel.setFont(fieldLabel.getFont().deriveFont(Font.BOLD));
		fieldLabel.setPreferredSize(new Dimension(labelSize, 20));
		final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		panel.add(fieldLabel);
		panel.add(comp);
		return panel;
	}

	private void setupAccessPermissions(AccessRestrictionType art) {
		if (AccessRestrictionType.NONE.equals(art)) {
			this.usersPalette.setEnabled(false);
			this.teamsPalette.setEnabled(false);

			this.allowAuthenticated.setEnabled(false);
			this.allowNamed.setEnabled(false);
			this.verifyCommitter.setEnabled(false);
		} else {
			this.allowAuthenticated.setEnabled(true);
			this.allowNamed.setEnabled(true);
			this.verifyCommitter.setEnabled(true);

			if (this.allowNamed.isSelected()) {
				this.usersPalette.setEnabled(true);
				this.teamsPalette.setEnabled(true);
			}
		}

	}

	private boolean validateFields() {
		String rname = this.nameField.getText();
		if (StringUtils.isEmpty(rname)) {
			error("Please enter a repository name!");
			return false;
		}

		// automatically convert backslashes to forward slashes
		rname = rname.replace('\\', '/');
		// Automatically replace // with /
		rname = rname.replace("//", "/");

		// prohibit folder paths
		if (rname.startsWith("/")) {
			error("Leading root folder references (/) are prohibited.");
			return false;
		}
		if (rname.startsWith("../")) {
			error("Relative folder references (../) are prohibited.");
			return false;
		}
		if (rname.contains("/../")) {
			error("Relative folder references (../) are prohibited.");
			return false;
		}
		if (rname.endsWith("/")) {
			rname = rname.substring(0, rname.length() - 1);
		}

		// confirm valid characters in repository name
		final Character c = StringUtils.findInvalidCharacter(rname);
		if (c != null) {
			error(MessageFormat.format("Illegal character ''{0}'' in repository name!", c));
			return false;
		}

		// verify repository name uniqueness on create
		if (this.isCreate) {
			// force repo names to lowercase
			// this means that repository name checking for rpc creation
			// is case-insensitive, regardless of the Gitblit server's
			// filesystem
			if (this.repositoryNames.contains(rname.toLowerCase())) {
				error(MessageFormat.format(
						"Can not create repository ''{0}'' because it already exists.", rname));
				return false;
			}
		} else {
			// check rename collision
			if (!this.repositoryName.equalsIgnoreCase(rname)) {
				if (this.repositoryNames.contains(rname.toLowerCase())) {
					error(MessageFormat.format(
							"Failed to rename ''{0}'' because ''{1}'' already exists.",
							this.repositoryName, rname));
					return false;
				}
			}
		}

		if (this.accessRestriction.getSelectedItem() == null) {
			error("Please select access restriction!");
			return false;
		}

		if (this.federationStrategy.getSelectedItem() == null) {
			error("Please select federation strategy!");
			return false;
		}

		this.repository.name = rname;
		this.repository.description = this.descriptionField.getText();
		this.repository.owners.clear();
		this.repository.owners.addAll(this.ownersPalette.getSelections());
		this.repository.HEAD = this.headRefField.getSelectedItem() == null ? null
				: this.headRefField.getSelectedItem().toString();
		this.repository.gcPeriod = (Integer) this.gcPeriod.getSelectedItem();
		this.repository.gcThreshold = this.gcThreshold.getText();
		this.repository.acceptNewPatchsets = this.acceptNewPatchsets.isSelected();
		this.repository.acceptNewTickets = this.acceptNewTickets.isSelected();
		this.repository.requireApproval = this.requireApproval.isSelected();
		this.repository.mergeTo = this.mergeToField.getSelectedItem() == null ? null : Repository
				.shortenRefName(this.mergeToField.getSelectedItem().toString());
		this.repository.useIncrementalPushTags = this.useIncrementalPushTags.isSelected();
		this.repository.showRemoteBranches = this.showRemoteBranches.isSelected();
		this.repository.skipSizeCalculation = this.skipSizeCalculation.isSelected();
		this.repository.skipSummaryMetrics = this.skipSummaryMetrics.isSelected();
		this.repository.maxActivityCommits = (Integer) this.maxActivityCommits.getSelectedItem();

		this.repository.isFrozen = this.isFrozen.isSelected();
		this.repository.allowForks = this.allowForks.isSelected();
		this.repository.verifyCommitter = this.verifyCommitter.isSelected();

		final String ml = this.mailingListsField.getText();
		if (!StringUtils.isEmpty(ml)) {
			final Set<String> list = new HashSet<String>();
			for (final String address : ml.split("(,|\\s)")) {
				if (StringUtils.isEmpty(address)) {
					continue;
				}
				list.add(address.toLowerCase());
			}
			this.repository.mailingLists = new ArrayList<String>(list);
		}

		this.repository.accessRestriction = (AccessRestrictionType) this.accessRestriction
				.getSelectedItem();
		this.repository.authorizationControl = this.allowAuthenticated.isSelected() ? AuthorizationControl.AUTHENTICATED
				: AuthorizationControl.NAMED;
		this.repository.federationStrategy = (FederationStrategy) this.federationStrategy
				.getSelectedItem();

		if (this.repository.federationStrategy.exceeds(FederationStrategy.EXCLUDE)) {
			this.repository.federationSets = this.setsPalette.getSelections();
		}

		this.repository.indexedBranches = this.indexedBranchesPalette.getSelections();
		this.repository.preReceiveScripts = this.preReceivePalette.getSelections();
		this.repository.postReceiveScripts = this.postReceivePalette.getSelections();

		// Custom Fields
		this.repository.customFields = new LinkedHashMap<String, String>();
		if (this.customTextfields != null) {
			for (final JTextField field : this.customTextfields) {
				final String key = field.getName();
				final String value = field.getText();
				this.repository.customFields.put(key, value);
			}
		}
		return true;
	}

	private void error(String message) {
		JOptionPane.showMessageDialog(EditRepositoryDialog.this, message,
				Translation.get("gb.error"), JOptionPane.ERROR_MESSAGE);
	}

	public void setAccessRestriction(AccessRestrictionType restriction) {
		this.accessRestriction.setSelectedItem(restriction);
		setupAccessPermissions(restriction);
	}

	public void setAuthorizationControl(AuthorizationControl authorization) {
		final boolean authenticated = (authorization != null)
				&& AuthorizationControl.AUTHENTICATED.equals(authorization);
		this.allowAuthenticated.setSelected(authenticated);
		this.allowNamed.setSelected(!authenticated);
	}

	public void setUsers(List<String> owners, List<String> all,
			List<RegistrantAccessPermission> permissions) {
		this.ownersPalette.setObjects(all, owners);
		this.usersPalette.setObjects(all, permissions);
	}

	public void setTeams(List<String> all, List<RegistrantAccessPermission> permissions) {
		this.teamsPalette.setObjects(all, permissions);
	}

	public void setRepositories(List<RepositoryModel> repositories) {
		this.repositoryNames.clear();
		for (final RepositoryModel repository : repositories) {
			// force repo names to lowercase
			// this means that repository name checking for rpc creation
			// is case-insensitive, regardless of the Gitblit server's
			// filesystem
			this.repositoryNames.add(repository.name.toLowerCase());
		}
	}

	public void setFederationSets(List<String> all, List<String> selected) {
		this.setsPalette.setObjects(all, selected);
	}

	public void setIndexedBranches(List<String> all, List<String> selected) {
		this.indexedBranchesPalette.setObjects(all, selected);
	}

	public void setPreReceiveScripts(List<String> all, List<String> inherited, List<String> selected) {
		this.preReceivePalette.setObjects(all, selected);
		showInherited(inherited, this.preReceiveInherited);
	}

	public void setPostReceiveScripts(List<String> all, List<String> inherited,
			List<String> selected) {
		this.postReceivePalette.setObjects(all, selected);
		showInherited(inherited, this.postReceiveInherited);
	}

	private static void showInherited(List<String> list, JLabel label) {
		final StringBuilder sb = new StringBuilder();
		if ((list != null) && (list.size() > 0)) {
			sb.append("<html><body><b>INHERITED</b><ul style=\"margin-left:5px;list-style-type: none;\">");
			for (final String script : list) {
				sb.append("<li>").append(script).append("</li>");
			}
			sb.append("</ul></body></html>");
		}
		label.setText(sb.toString());
	}

	public RepositoryModel getRepository() {
		if (this.canceled) {
			return null;
		}
		return this.repository;
	}

	public List<RegistrantAccessPermission> getUserAccessPermissions() {
		return this.usersPalette.getPermissions();
	}

	public List<RegistrantAccessPermission> getTeamAccessPermissions() {
		return this.teamsPalette.getPermissions();
	}

	public void setCustomFields(RepositoryModel repository, Map<String, String> customFields) {
		this.customFieldsPanel.removeAll();
		this.customTextfields = new ArrayList<JTextField>();

		final Insets insets = new Insets(5, 5, 5, 5);
		final JPanel fields = new JPanel(new GridLayout(0, 1, 0, 5)) {

			private static final long serialVersionUID = 1L;

			@Override
			public Insets getInsets() {
				return insets;
			}
		};

		for (final Map.Entry<String, String> entry : customFields.entrySet()) {
			final String field = entry.getKey();
			String value = "";
			if ((repository.customFields != null) && repository.customFields.containsKey(field)) {
				value = repository.customFields.get(field);
			}
			final JTextField textField = new JTextField(value);
			textField.setName(field);

			textField.setPreferredSize(new Dimension(450, 26));

			fields.add(newFieldPanel(entry.getValue(), 250, textField));

			this.customTextfields.add(textField);
		}
		final JScrollPane jsp = new JScrollPane(fields);
		jsp.getVerticalScrollBar().setBlockIncrement(100);
		jsp.getVerticalScrollBar().setUnitIncrement(100);
		jsp.setViewportBorder(null);
		this.customFieldsPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		this.customFieldsPanel.add(jsp);
	}

	/**
	 * ListCellRenderer to display descriptive text about the access
	 * restriction.
	 *
	 */
	private class AccessRestrictionRenderer extends DefaultListCellRenderer {

		private static final long serialVersionUID = 1L;

		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus) {
			super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

			if (value instanceof AccessRestrictionType) {
				final AccessRestrictionType restriction = (AccessRestrictionType) value;
				switch (restriction) {
				case NONE:
					setText(Translation.get("gb.notRestricted"));
					break;
				case PUSH:
					setText(Translation.get("gb.pushRestricted"));
					break;
				case CLONE:
					setText(Translation.get("gb.cloneRestricted"));
					break;
				case VIEW:
					setText(Translation.get("gb.viewRestricted"));
					break;
				}
			} else {
				setText(value.toString());
			}
			return this;
		}
	}

	/**
	 * ListCellRenderer to display descriptive text about the federation
	 * strategy.
	 */
	private class FederationStrategyRenderer<E> extends JLabel implements ListCellRenderer<E> {

		private static final long serialVersionUID = 1L;

		@Override
		public Component getListCellRendererComponent(JList<? extends E> list, E value, int index,
				boolean isSelected, boolean cellHasFocus) {
			if (value instanceof FederationStrategy) {
				final FederationStrategy strategy = (FederationStrategy) value;
				switch (strategy) {
				case EXCLUDE:
					setText(Translation.get("gb.excludeFromFederation"));
					break;
				case FEDERATE_THIS:
					setText(Translation.get("gb.federateThis"));
					break;
				case FEDERATE_ORIGIN:
					setText(Translation.get("gb.federateOrigin"));
					break;
				}
			} else {
				setText(value.toString());
			}
			return this;
		}
	}
}
