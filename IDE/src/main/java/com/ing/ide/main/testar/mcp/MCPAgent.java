package com.ing.ide.main.testar.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ing.datalib.component.Project;
import okhttp3.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MCPAgent {

	private static final Logger logger = LogManager.getLogger();

	private final String OPENAI_API_KEY = System.getenv("OPENAI_API");
	private final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
	private final OkHttpClient client = new OkHttpClient();
	private final ObjectMapper mapper = new ObjectMapper();

	private final int maxActions;
	private final String openaiModel;
	private final String bddInstructions;
	private final MCPInterface mcpInterface;

	public MCPAgent(Project project, String openaiModel, int maxActions, String bddInstructions) {
		this.openaiModel = openaiModel;
		this.maxActions = maxActions;
		this.bddInstructions = bddInstructions;
		this.mcpInterface = new MCPInterface(project);
	}

	public String runLLMAgent() {
		if (OPENAI_API_KEY == null) {
			throw new IllegalStateException("Missing OPENAI_API env var.");
		}

		List<Map<String, Object>> messages = defineMessages();
		List<Map<String, Object>> functions = defineFunctions();

		for (int step = 0; step < this.maxActions; step++) {
			Map<String, Object> body = new HashMap<>();
			body.put("model", openaiModel);
			body.put("messages", messages);
			body.put("functions", functions);
			body.put("function_call", "auto");

			try (Response response = client.newCall(
					new Request.Builder()
					.url(OPENAI_API_URL)
					.header("Authorization", "Bearer " + OPENAI_API_KEY)
					.header("Content-Type", "application/json")
					.post(RequestBody.create(MediaType.parse("application/json"), mapper.writeValueAsString(body)))
					.build()
			).execute()) {
				if(response.isSuccessful()) {
					String json = response.body().string();
					Map<?, ?> parsed = mapper.readValue(json, Map.class);
					Map<?, ?> choice = ((List<Map<?, ?>>) parsed.get("choices")).get(0);
					Map<?, ?> message = (Map<?, ?>) choice.get("message");

					Map<?, ?> functionCall = (Map<?, ?>) message.get("function_call");

					// Don't stop, give the LLM another chance
					if (functionCall == null) {
						String feedback = "ISSUE: No function was selected. Please review the last function results or feedback messages.";
						logger.log(Level.ERROR, feedback);

						Map<String, Object> functionMsg = new HashMap<>();
						functionMsg.put("role", "function");
						functionMsg.put("name", "undefined");
						functionMsg.put("content", feedback);
						messages.add(functionMsg);

						Map<String, Object> userHint = new HashMap<>();
						userHint.put("role", "user");
						userHint.put("content", "Reminder: You must choose a valid function to proceed.");
						messages.add(userHint);

						continue;
					}

					String functionName = (String) functionCall.get("name");
					String argumentsJson = (String) functionCall.get("arguments");
					Map<String, Object> arguments = mapper.readValue(argumentsJson, Map.class);

					logger.log(Level.ERROR, "DEBUG functionName: " + functionName);
					logger.log(Level.ERROR, "DEBUG argumentsJson: " + argumentsJson);

					String result;
					boolean stopExecution = false;
					String feedbackMessage = "";
					switch (functionName) {
						case "loadWebURL":
							result = mcpInterface.loadWebURL((String) arguments.get("url"));
							logger.log(Level.ERROR, "loadWebURL: " + result);
							break;
						case "getCurrentURL":
							result = mcpInterface.getCurrentURL();
							logger.log(Level.ERROR, "getCurrentURL: " + result);
							break;
						case "navigateBack":
							result = mcpInterface.navigateBack();
							logger.log(Level.ERROR, "navigateBack: " + result);
							break;
						case "getState":
							result = mcpInterface.getState();
							logger.log(Level.ERROR, "getState: " + result);
							break;
						case "executeClickAction":
							result = mcpInterface.executeClickAction(
									(String) arguments.get("bddStep"),
									(String) arguments.get("cssSelector")
							);
							logger.log(Level.ERROR, "executeClickAction: " + result);
							break;
						case "executeFillAction":
							result = mcpInterface.executeFillAction(
									(String) arguments.get("bddStep"),
									(String) arguments.get("cssSelector"),
									(String) arguments.get("fillText")
							);
							logger.log(Level.ERROR, "executeFillAction: " + result);
							break;
						case "executeSelectAction":
							result = mcpInterface.executeSelectAction(
									(String) arguments.get("bddStep"),
									(String) arguments.get("cssSelector"),
									(String) arguments.get("optionValue")
							);
							logger.log(Level.ERROR, "executeSelectAction: " + result);
							break;
						case "checkExecutedActions":
							result = mcpInterface.checkExecutedActions();
							logger.log(Level.ERROR, "checkExecutedActions: " + result);
							break;
						case "getStateImage":
							String base64 = mcpInterface.getStateImage();
							if (!base64.isEmpty()) {
								Map<String, Object> imageMsg = new HashMap<>();
								imageMsg.put("role", "user");

								List<Map<String, Object>> content = new ArrayList<>();
								content.add(Map.of("type", "text", "text", "Here is the current GUI state."));

								Map<String, Object> imageUrl = new HashMap<>();
								imageUrl.put("url", "data:image/png;base64," + base64);
								content.add(Map.of("type", "image_url", "image_url", imageUrl));

								imageMsg.put("content", content);
								messages.add(imageMsg);

								result = "Screenshot attached!";
							} else {
								result = "ISSUE: Screenshot failed to obtain.";
							}
							logger.log(Level.ERROR, "getStateImage: " + result);
							break;
						case "addFinalAssert":
							result = mcpInterface.addFinalAssert(
									(String) arguments.get("bddStep"),
									(String) arguments.get("assertText")
							);
							logger.log(Level.ERROR, "addFinalAssert: " + result);

							if ("OK".equals(result)) {
								stopExecution = true;
							} else {
								feedbackMessage = "The final text assertion failed. Try again with a correct text locator.";
							}

							break;
						default:
							result = "Unknown function: " + functionName;
							feedbackMessage = "The function was not recognized. Check the available options.";
							logger.log(Level.ERROR, result);
					}

					Map<String, Object> assistantMsg = new HashMap<>();
					assistantMsg.put("role", "assistant");
					assistantMsg.put("content", null);
					Map<String, Object> funcCall = new HashMap<>();
					funcCall.put("name", functionName);
					funcCall.put("arguments", argumentsJson);
					assistantMsg.put("function_call", funcCall);
					messages.add(assistantMsg);

					Map<String, Object> functionMsg = new HashMap<>();
					functionMsg.put("role", "function");
					functionMsg.put("name", functionName);
					functionMsg.put("content", result != null ? result : "null");
					messages.add(functionMsg);

					// Extra guidance feedback needed
					if (!feedbackMessage.isEmpty()) {
						Map<String, Object> userReminder = new HashMap<>();
						userReminder.put("role", "user");
						userReminder.put("content", feedbackMessage);
						messages.add(userReminder);
					}

					// Stop execution flag
					if (stopExecution) {
						mcpInterface.shutdown();
						return result;
					}
				} else if (response.code() == 429) {
					String retryAfter = response.header("Retry-After");
					long waitTime = 10000L; // default 10 seconds

					if (retryAfter != null) {
						try {
							waitTime = Long.parseLong(retryAfter) * 1333L; // conversion with 33% margin
						} catch (NumberFormatException e) {
							logger.log(Level.ERROR, "Invalid Retry-After header value: " + retryAfter);
						}
					}

					logger.log(Level.ERROR, "OpenAI rate limited (429)... wait " + (waitTime / 1000) + " seconds...");
					Thread.sleep(waitTime);
				} else {
					String failed = "Stop execution due to OpenAI call fail: " + response.code();
					logger.log(Level.ERROR, failed);
					return failed;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		mcpInterface.shutdown();
		logger.log(Level.ERROR, "maxAction executed");
		return "maxAction executed";
	}

	private List<Map<String, Object>> defineMessages() {
		List<Map<String, Object>> messages = new ArrayList<>();

		messages.add(Map.of(
				"role", "system",
				"content", "You are a BDD-GUI test agent. " +
						"Your goal is to complete the BDD instructions. " +
						"Use loadWebURL, getState, executeClickAction, executeFillAction, and executeSelectAction functions. " +
						"Use getCurrentURL and checkExecutedActions functions if you need assistance. " +
						"Use navigateBack function if you need to control the web browser. " +
						"When considering the BDD test is completed use the getStateImage and addFinalAssert functions.")
		);

		messages.add(Map.of(
				"role", "user",
				"content", "Begin by load the web url to be tested.")
		);

		messages.add(Map.of(
				"role", "user",
				"content", "Get the current GUI state to obtain the available web elements.")
		);

		messages.add(Map.of(
				"role", "user",
				"content", this.bddInstructions)
		);

		return messages;
	}

	private List<Map<String, Object>> defineFunctions() {
		List<Map<String, Object>> functions = new ArrayList<>();

		functions.add(Map.of(
				"name", "loadWebURL",
				"description", "Load the web URL to be tested.",
				"parameters", Map.of(
						"type", "object",
						"properties", Map.of(
								"url", Map.of(
										"type", "string",
										"url", "The web url to be tested."
								)
						),
						"required", List.of("url")
				)
		));

		functions.add(Map.of(
				"name", "getCurrentURL",
				"description", "Get the current web url that is being tested.",
				"parameters", Map.of(
						"type", "object",
						"properties", new HashMap<>()
				)
		));

		functions.add(Map.of(
				"name", "navigateBack",
				"description", "Navigate to the previous web page in history.",
				"parameters", Map.of(
						"type", "object",
						"properties", new HashMap<>()
				)
		));

		functions.add(Map.of(
				"name", "getState",
				"description", "Get a list of current interactive GUI web elements with CSS selector, visible text, tag type, and accessibility attributes.",
				"parameters", Map.of(
						"type", "object",
						"properties", new HashMap<>()
				)
		));

		functions.add(Map.of(
				"name", "executeClickAction",
				"description", "Use a CSS selector to click an element.",
				"parameters", Map.of(
						"type", "object",
						"properties", Map.of(
								"bddStep", Map.of(
										"type", "string",
										"description", "The BDD step text associated with this action."
								),
								"cssSelector", Map.of(
										"type", "string",
										"description", "The CSS selector of the clickable element."
								)
						),
						"required", List.of("bddStep", "cssSelector")
				)
		));

		functions.add(Map.of(
				"name", "executeFillAction",
				"description", "Use a CSS selector to fill text into an input element.",
				"parameters", Map.of(
						"type", "object",
						"properties", Map.of(
								"bddStep", Map.of(
										"type", "string",
										"description", "The BDD step text associated with this action."
								),
								"cssSelector", Map.of(
										"type", "string",
										"description", "The CSS selector of the input element to be filled."
								),
								"fillText", Map.of(
										"type", "string",
										"description", "The text to be entered into the input element."
								)
						),
						"required", List.of("bddStep", "cssSelector", "fillText")
				)
		));

		functions.add(Map.of(
				"name", "executeSelectAction",
				"description", "Use a CSS selector to select an option value into a select element.",
				"parameters", Map.of(
						"type", "object",
						"properties", Map.of(
								"bddStep", Map.of(
										"type", "string",
										"description", "The BDD step text associated with this action."
								),
								"cssSelector", Map.of(
										"type", "string",
										"description", "The CSS selector of the input element to be filled."
								),
								"optionValue", Map.of(
										"type", "string",
										"description", "The option value to be selected into the select element."
								)
						),
						"required", List.of("bddStep", "cssSelector", "optionValue")
				)
		));

		functions.add(Map.of(
				"name", "checkExecutedActions",
				"description", "Check the information of the executed actions during the BDD test process.",
				"parameters", Map.of(
						"type", "object",
						"properties", new HashMap<>()
				)
		));

		functions.add(Map.of(
				"name", "getStateImage",
				"description", "Get an image of the current GUI state.",
				"parameters", Map.of(
						"type", "object",
						"properties", new HashMap<>()
				)
		));

		functions.add(Map.of(
				"name", "addFinalAssert",
				"description", "Add a unique text assert as final step in the BDD test execution.",
				"parameters", Map.of(
						"type", "object",
						"properties", Map.of(
								"bddStep", Map.of(
										"type", "string",
										"description", "The BDD step text associated with this action."
								),
								"assertText", Map.of(
										"type", "string",
										"assertText", "A visible unique text that can be used as text locator assert."
								)
						),
						"required", List.of("bddStep", "assertText")
				)
		));

		return functions;
	}

}
