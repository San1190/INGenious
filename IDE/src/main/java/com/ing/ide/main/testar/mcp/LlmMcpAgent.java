package com.ing.ide.main.testar.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ing.datalib.component.Project;
import com.ing.ide.main.testar.mcp.helper.McpFunctionBuilder;
import com.ing.ide.main.testar.mcp.helper.McpFunctionExecutor;
import com.ing.ide.main.testar.mcp.helper.McpNames;
import okhttp3.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LlmMcpAgent {

	private static final Logger logger = LogManager.getLogger();

	private final String OPENAI_API_KEY = System.getenv("OPENAI_API");
	private final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
	private final OkHttpClient client = new OkHttpClient();
	private final ObjectMapper mapper = new ObjectMapper();

	private final int maxActions;
	private final String openaiModel;
	private final String bddInstructions;
	private final McpInterface mcpInterface;

	public LlmMcpAgent(Project project, String openaiModel, int maxActions, String bddInstructions) {
		this.openaiModel = openaiModel;
		this.maxActions = maxActions;
		this.bddInstructions = bddInstructions;
		this.mcpInterface = new PlaywrightMcpDriver(project);
	}

	public String runLLMAgent() {
		if (OPENAI_API_KEY == null) {
			throw new IllegalStateException("Missing OPENAI_API env var.");
		}

		// Prepare the messages and functions data to be sent to the LLM
		final ObjectMapper mapper = new ObjectMapper();
		final List<Map<String, Object>> messages  = defineMessages();
		final List<Map<String, Object>> functions = McpFunctionBuilder.from(McpInterface.class);
		final McpFunctionExecutor<McpInterface> executor = McpFunctionExecutor.of(McpInterface.class, mcpInterface, mapper);

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

				if (!response.isSuccessful()) {
					if (response.code() == 429) {
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
				}

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
				logger.log(Level.ERROR, "DEBUG functionName: " + functionName);

				String argumentsJson = (String) functionCall.get("arguments");
				logger.log(Level.ERROR, "DEBUG argumentsJson: " + argumentsJson);

				// Execute the functions using the generic executor
				Object resultObj = executor.execute(functionName, argumentsJson);
				String result = (resultObj == null) ? "null" : resultObj.toString();

				// Attach the state image as base64
				if (McpNames.of(McpInterface::getStateImage).equals(functionName)) {
					if (!result.isEmpty()) {
						attachStateImage(messages, result);
						result = "Screenshot attached!";
					} else {
						result = "ISSUE: Screenshot failed to obtain.";
					}
					logger.log(Level.ERROR, "getStateImage: " + result);
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

			} catch (Exception e) {
				logger.log(Level.ERROR, "LLM step failed", e);
				e.printStackTrace();
			}
		}

		mcpInterface.stopTestExecution();
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

	private static void attachStateImage(List<Map<String, Object>> messages, String base64Png) {
		Map<String, Object> imageMsg = new HashMap<>();
		imageMsg.put("role", "user");

		List<Map<String, Object>> content = new ArrayList<>();
		content.add(Map.of("type", "text", "text", "Here is the current GUI state."));
		Map<String, Object> imageUrl = new HashMap<>();
		imageUrl.put("url", "data:image/png;base64," + base64Png);
		content.add(Map.of("type", "image_url", "image_url", imageUrl));

		imageMsg.put("content", content);
		messages.add(imageMsg);
	}

}
