package com.ing.ide.main.testar.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ing.datalib.component.Project;
import com.ing.ide.main.testar.mcp.helper.McpToolBuilder;
import com.ing.ide.main.testar.mcp.helper.McpToolExecutor;
import com.ing.ide.main.testar.mcp.helper.McpNames;
import okhttp3.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

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

		// Prepare the messages and tools data to be sent to the LLM
		final List<Map<String, Object>> messages  = defineMessages();
		final List<Map<String, Object>> tools = McpToolBuilder.from(McpInterface.class);
		final McpToolExecutor<McpInterface> executor = McpToolExecutor.of(McpInterface.class, mcpInterface, mapper);

		for (int step = 0; step < this.maxActions; step++) {
			Map<String, Object> body = new HashMap<>();
			body.put("model", openaiModel);
			body.put("messages", messages);
			body.put("tools", tools);
			body.put("tool_choice", "auto");

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
						logger.log(Level.ERROR, response.body().string());
						return failed;
					}
				}

				String json = Objects.requireNonNull(response.body()).string();
				Map<?, ?> parsed = mapper.readValue(json, Map.class);
				Map<?, ?> choice = ((List<Map<?, ?>>) parsed.get("choices")).get(0);
				Map<?, ?> message = (Map<?, ?>) choice.get("message");

				messages.add((Map<String, Object>) message);

				// Read all tool_calls (array)
				List<Map<?, ?>> toolCalls = (List<Map<?, ?>>) message.get("tool_calls");

				// Empty toolCalls response. Don't stop, give the LLM another chance
				if (toolCalls == null || toolCalls.isEmpty()) {
					String feedback = "ISSUE: No tool was selected. Please review the last results.";
					logger.log(Level.ERROR, feedback);
					messages.add(Map.of(
							"role", "user",
							"content", "Reminder: choose a valid tool to proceed."
					));
					continue;
				}

				// Execute all tool calls
				for (Map<?, ?> toolCall : toolCalls) {
					String callId = (String) toolCall.get("id");
					Map<?, ?> function = (Map<?, ?>) toolCall.get("function");
					String toolName = (String) function.get("name");
					Object rawArgs = function.get("arguments");
					String argumentsJson = (rawArgs instanceof CharSequence)
							? rawArgs.toString()
							: mapper.writeValueAsString(rawArgs);

					logger.log(Level.ERROR, "DEBUG toolName: " + toolName);
					logger.log(Level.ERROR, "DEBUG argumentsJson: " + argumentsJson);

					Object resultObj = executor.execute(toolName, argumentsJson);
					String result = (resultObj == null) ? "null" : resultObj.toString();

					// Check if stop the execution due to the LLM decision
					if(McpNames.of(McpInterface::stopTestExecution).equals(toolName)){
						logger.log(Level.ERROR, "LLM agent decided to stop the test execution");
						return "LLM agent decided to stop the test execution";
					}

					// Reply to this tool call first
					boolean requireStateImage = McpNames.of(McpInterface::getStateImage).equals(toolName);
					String toolContent = requireStateImage ? "screenshot_ready" : result;
					messages.add(Map.of(
							"role", "tool",
							"tool_call_id", callId,
							"content", toolContent
					));

					// If required, additionally add the image user message
					if (requireStateImage && !result.isEmpty() && supportsVision(openaiModel)) {
						attachStateImage(messages, result);
					} else if (requireStateImage && !result.isEmpty()) {
						messages.add(Map.of(
								"role","user",
								"content","Screenshot captured (omitted for this model)."
						));
					}
				}

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

	private boolean supportsVision(String model) {
		String m = model == null ? "" : model.toLowerCase();
		return m.contains("gpt-4o") || m.contains("gpt-4.1") || m.contains("gpt-5");
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
