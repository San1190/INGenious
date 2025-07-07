package com.ing.ide.main.testar.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ing.datalib.component.Project;
import com.ing.datalib.or.ObjectRepository;
import com.ing.datalib.or.web.WebOR;
import okhttp3.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MCPAgent {
	private static final String OPENAI_API_KEY = System.getenv("OPENAI_API");
	private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
	private static final OkHttpClient client = new OkHttpClient();
	private static final ObjectMapper mapper = new ObjectMapper();


	private static final Logger logger = LogManager.getLogger();

	private final Project project;
	private final ObjectRepository objectRepository;
	private final WebOR webOR;
	private final String webSUT;
	private final int maxActions;
	private final String bddInstructions;
	private final MCPInterface mcpInterface;

	public MCPAgent(Project project, String webSUT, int maxActions, String bddInstructions) {
		this.project = project;
		this.webSUT = webSUT;
		this.maxActions = maxActions;
		this.bddInstructions = bddInstructions;

		this.mcpInterface = new MCPInterface(project);

		// Prepare the INGenious object repository used to store steps information
		objectRepository = new ObjectRepository(project);
		webOR = objectRepository.getWebOR();
		webOR.setObjectRepository(objectRepository);
	}

	public String runLLMAgent() {
		if (OPENAI_API_KEY == null) {
			throw new IllegalStateException("Missing OPENAI_API env var.");
		}

		List<Map<String, Object>> messages = new ArrayList<>();
		messages.add(Map.of("role", "system", "content", "You are a BDD-GUI test agent. " +
				"Your goal is to complete the BDD instructions. " +
				"Use loadWebURL, getState, executeClickAction, and executeFillAction functions. " +
				"When considering the BDD test is completed use the getStateImage and stopTest functions."));
		messages.add(Map.of("role", "user", "content", "Begin by load the web url to be tested."));
		messages.add(Map.of("role", "user", "content", "Get the current GUI state to obtain the available web elements."));
		messages.add(Map.of("role", "user", "content", this.bddInstructions));

		List<Map<String, Object>> functions = defineFunctions();

		for (int step = 0; step < this.maxActions; step++) {
			Map<String, Object> body = new HashMap<>();
			body.put("model", "gpt-4o");
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
					String failed = "OpenAI call failed: " + response.code();
					logger.log(Level.ERROR, failed);
					return failed;
				}

				String json = response.body().string();
				Map<?, ?> parsed = mapper.readValue(json, Map.class);
				Map<?, ?> choice = ((List<Map<?, ?>>) parsed.get("choices")).get(0);
				Map<?, ?> message = (Map<?, ?>) choice.get("message");

				Map<?, ?> functionCall = (Map<?, ?>) message.get("function_call");

				if (functionCall == null) {
					String empty = "Empty functionCall";
					logger.log(Level.ERROR, empty);
					mcpInterface.shutdown();
					return empty;
				}

				String functionName = (String) functionCall.get("name");
				String argumentsJson = (String) functionCall.get("arguments");
				Map<String, Object> arguments = mapper.readValue(argumentsJson, Map.class);

				logger.log(Level.ERROR, "DEBUG functionName: " + functionName);
				logger.log(Level.ERROR, "DEBUG argumentsJson: " + argumentsJson);

				String result;
				switch (functionName) {
					case "loadWebURL":
						result = String.valueOf(mcpInterface.loadWebURL((String) arguments.get("url")));
						logger.log(Level.ERROR, "loadWebURL: " + result);
						break;
					case "getState":
						result = String.join(", ", mcpInterface.getState());
						logger.log(Level.ERROR, "getState: " + result);
						break;
					case "executeClickAction":
						result = mcpInterface.executeClickAction((String) arguments.get("cssSelector"));
						logger.log(Level.ERROR, "executeClickAction: " + result);
						break;
					case "executeFillAction":
						result = mcpInterface.executeFillAction(
								(String) arguments.get("cssSelector"),
								(String) arguments.get("fillText")
								);
						logger.log(Level.ERROR, "executeFillAction: " + result);
						break;
					case "getStateImage":
						String base64 = mcpInterface.getStateImage();
						if (base64 != null) {
							Map<String, Object> imageMsg = new HashMap<>();
							imageMsg.put("role", "user");

							List<Map<String, Object>> content = new ArrayList<>();
							content.add(Map.of("type", "text", "text", "Here is the current GUI state."));

							Map<String, Object> imageUrl = new HashMap<>();
							imageUrl.put("url", "data:image/png;base64," + base64);
							content.add(Map.of("type", "image_url", "image_url", imageUrl));

							imageMsg.put("content", content);
							messages.add(imageMsg);

							result = "Screenshot attached.";
						} else {
							result = "Screenshot failed.";
						}
						logger.log(Level.ERROR, "getStateImage: " + result);
						break;
					case "stopTest":
						String verdict = (String) arguments.get("verdict");
						mcpInterface.stopTest(verdict);
						logger.log(Level.ERROR, "stopTest: " + verdict);
						return verdict;
					default:
						String unknown = "Unknown function: " + functionName;
						logger.log(Level.ERROR, unknown);
						mcpInterface.shutdown();
						return unknown;
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
				e.printStackTrace();
				break;
			}
		}
		mcpInterface.shutdown();
		logger.log(Level.ERROR, "maxAction executed");
		return "maxAction executed";
	}

	private static List<Map<String, Object>> defineFunctions() {
		List<Map<String, Object>> functions = new ArrayList<>();

		functions.add(Map.of(
				"name", "loadWebURL",
				"description", "Load the web URL to be tested",
				"parameters", Map.of(
						"type", "object",
						"properties", Map.of("url", Map.of("type", "string", "url", "The web url to be tested")),
						"required", List.of("url")
				)
		));

		functions.add(Map.of(
				"name", "getState",
				"description", "Get all CSS selectors of clickable GUI elements from the current page.",
				"parameters", Map.of("type", "object", "properties", new HashMap<>())
		));

		functions.add(Map.of(
				"name", "executeClickAction",
				"description", "Use a CSS selector to click an element.",
				"parameters", Map.of(
						"type", "object",
						"properties", Map.of("cssSelector", Map.of("type", "string", "cssSelector", "The visible cssSelector of clickable element.")),
						"required", List.of("cssSelector")
				)
		));

		functions.add(Map.of(
				"name", "executeFillAction",
				"description", "Use a CSS selector to fill text into an element.",
				"parameters", Map.of(
						"type", "object",
						"properties", Map.of(
								"cssSelector", Map.of(
										"type", "string",
										"description", "The CSS selector of the input element to be filled."
								),
								"fillText", Map.of(
										"type", "string",
										"description", "The text to be entered into the input element."
								)
						),
						"required", List.of("cssSelector", "fillText")
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
				"name", "stopTest",
				"description", "Stop the test execution if considering the BDD test is completed.",
				"parameters", Map.of(
						"type", "object",
						"properties", Map.of("verdict", Map.of("type", "string", "verdict", "The verdict decision to stop the test execution")),
						"required", List.of("verdict")
				)
		));

		return functions;
	}

}
