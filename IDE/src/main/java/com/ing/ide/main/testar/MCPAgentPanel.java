package com.ing.ide.main.testar;

import com.ing.ide.main.mainui.AppMainFrame;
import com.ing.ide.main.testar.mcp.LlmMcpAgent;
import com.ing.ide.settings.IconSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MCPAgentPanel {

	private final AppMainFrame sMainFrame;

	private String defaultBDDText =
			"Given the user navigates to the url 'https://para.testar.org/'\n" +
			"When the user navigates to the contact page\n" +
			"And the user fills the customer care form with valid data\n" +
			"And the user submits the customer care form\n" +
			"Then a message indicating the a representative will be contacting you appears";

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

		JLabel openaiLabel = new JLabel("OpenAI model:");
		JTextField openaiTextField = new JTextField(40);
		openaiTextField.setText("gpt-4o");
		formPanel.add(openaiLabel);
		formPanel.add(openaiTextField);

		JLabel actionsLabel = new JLabel("Max Actions:");
		JSpinner actionsSpinner = new JSpinner();
		actionsSpinner.setValue(20);
		formPanel.add(actionsLabel);
		formPanel.add(actionsSpinner);

		inputPanel.add(formPanel, BorderLayout.NORTH);

		// Add a BDD Instructions text area with scroll
		JLabel bddLabel = new JLabel("BDD Instructions:");

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
				String openaiModel = openaiTextField.getText().trim();
				int maxActions = (Integer) actionsSpinner.getValue();
				String bddInstructions = bddTextArea.getText();

				// Save possible updated BDD text
				defaultBDDText = bddInstructions;

				LlmMcpAgent llmMcpAgent = new LlmMcpAgent(
						sMainFrame.getProject(),
						openaiModel,
						maxActions,
						bddInstructions);

				SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
					@Override
					protected String doInBackground() throws Exception {
						return llmMcpAgent.runLLMAgent();
					}
				};
				worker.execute();
			}
		});

		closeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// Save possible updated BDD text
				defaultBDDText = bddTextArea.getText();

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
