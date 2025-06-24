package com.ing.ide.main.testar;

import com.ing.ide.main.mainui.AppMainFrame;
import com.ing.ide.settings.IconSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedHashMap;
import java.util.Map;

public class MCPAgentPanel {

	private final AppMainFrame sMainFrame;

	public MCPAgentPanel(AppMainFrame sMainFrame) {
		this.sMainFrame = sMainFrame;
	}

	public void openEditor() {
		// Create a modal dialog
		JDialog dialog = new JDialog(sMainFrame, "TESTAR MCP Scriptless agent", true);
		dialog.setSize(500, 600);
		dialog.setLayout(new BorderLayout());
		dialog.add(new JLabel(IconSettings.getIconSettings().getTESTARIcon()), BorderLayout.NORTH);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		// Create a panel for the input components
		JPanel inputPanel = new JPanel();
		inputPanel.setLayout(new BorderLayout());

		JPanel formPanel = new JPanel(new GridLayout(3, 2, 5, 5));
		JLabel urlLabel = new JLabel("Enter URL:");
		JTextField urlTextField = new JTextField(40);
		urlTextField.setText("");
		formPanel.add(urlLabel);
		formPanel.add(urlTextField);

		JLabel actionsLabel = new JLabel("Max Actions:");
		JSpinner actionsSpinner = new JSpinner();
		actionsSpinner.setValue(10);
		formPanel.add(actionsLabel);
		formPanel.add(actionsSpinner);

		inputPanel.add(formPanel, BorderLayout.NORTH);

		// Add a BDD Instructions text area with scroll
		JLabel bddLabel = new JLabel("BDD Instructions:");
		String defaultBDDText =
				"Given the user navigates to the url 'https://para.testar.org/'\n" +
				"When the user fill 'john' on the username text field\n" +
				"When the user fill 'demo' on the password text field\n" +
				"When the user clicks on the 'Login' button\n" +
				"Then the link 'Request Loan' should be displayed";

		JTextArea bddTextArea = new JTextArea(defaultBDDText, 10, 40);
		bddTextArea.setLineWrap(true);
		bddTextArea.setWrapStyleWord(true);
		JScrollPane bddScrollPane = new JScrollPane(bddTextArea);

		JPanel bddPanel = new JPanel(new BorderLayout());
		bddPanel.add(bddLabel, BorderLayout.NORTH);
		bddPanel.add(bddScrollPane, BorderLayout.CENTER);

		inputPanel.add(bddPanel, BorderLayout.CENTER);


		// Create a panel for buttons
		JPanel buttonPanel = new JPanel();
		JButton launchButton = new JButton("Launch");
		JButton closeButton = new JButton("Close");

		launchButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String url = urlTextField.getText();
				int maxActions = (Integer) actionsSpinner.getValue();
				String bddInstructions = bddTextArea.getText();

				MCPAgent mcpAgent = new MCPAgent(
						sMainFrame.getProject(),
						url,
						maxActions,
						bddInstructions);

				SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
					@Override
					protected String doInBackground() throws Exception {
						return mcpAgent.runLLMAgent();
					}
				};
				worker.execute();
			}
		});

		closeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				dialog.dispose();
				sMainFrame.checkAndLoadRecent();
			}
		});

		buttonPanel.add(launchButton);
		buttonPanel.add(closeButton);

		dialog.add(inputPanel, BorderLayout.CENTER);
		dialog.add(buttonPanel, BorderLayout.SOUTH);

		dialog.setLocationRelativeTo(sMainFrame);
		dialog.setVisible(true);
	}
}
