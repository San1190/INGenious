package com.ing.ide.main.testar.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ing.datalib.component.Project;
import com.ing.ide.main.testar.mcp.helper.McpNames;
import com.ing.ide.main.testar.mcp.helper.McpToolBuilder;
import com.ing.ide.main.testar.mcp.helper.McpToolExecutor;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LLM-based Model Context Protocol (MCP) Agent for BDD-GUI testing.
 * 
 * <p>
 * This class orchestrates the interaction between a Large Language Model (LLM)
 * and the MCP interface to execute BDD (Behavior-Driven Development) test
 * scenarios
 * on web applications using GUI automation.
 * </p>
 * 
 * <h2>Multi-LLM Architecture</h2>
 * <p>
 * This class is part of a <strong>Strategy Pattern</strong> implementation that
 * supports
 * multiple LLM providers for the following business reasons:
 * </p>
 * <ul>
 * <li><strong>GDPR Compliance</strong>: Switch to privacy-focused or on-premise
 * models</li>
 * <li><strong>Cost Optimization</strong>: Use different providers based on
 * pricing</li>
 * <li><strong>Flexibility</strong>: Easy integration of new LLM providers</li>
 * </ul>
 * 
 * <p>
 * The LLM provider is abstracted via the {@link LlmProvider} interface,
 * allowing
 * seamless switching between OpenAI, Google Gemini, Meta Llama, or any other
 * provider
 * without modifying this class.
 * </p>
 * 
 * <h2>Supported Providers</h2>
 * <ul>
 * <li>{@link OpenAiProvider} - OpenAI and Azure OpenAI</li>
 * <li>GeminiProvider - Google Gemini (future)</li>
 * <li>LlamaProvider - Meta Llama via local or cloud (future)</li>
 * </ul>
 * 
 * @author TFG-MCP-TESTAR Team
 * @version 2.0
 * @since 2024
 * @see LlmProvider
 * @see OpenAiProvider
 * @see McpInterface
 */
public class LlmMcpAgent {

    private static final Logger LOGGER = Logger.getLogger(LlmMcpAgent.class.getName());

    private final ObjectMapper mapper = new ObjectMapper();

    /** The LLM provider implementing the Strategy pattern */
    private final LlmProvider aiProvider;

    private final int maxActions;
    private final String bddInstructions;
    private final McpInterface mcpInterface;

    /**
     * Constructs a new LlmMcpAgent with the specified configuration.
     * 
     * <p>
     * This constructor creates an {@link OpenAiProvider} as the default LLM
     * provider.
     * To use a different provider, use the alternative constructor that accepts
     * a {@link LlmProvider} instance.
     * </p>
     * 
     * @param project              the INGenious project context
     * @param openaiApiUrl         the OpenAI API endpoint URL
     * @param openaiApiKeyVariable the name of the environment variable containing
     *                             the API key
     * @param openaiModel          the model identifier (e.g., "gpt-4o")
     * @param vision               whether vision/image input is enabled
     * @param reasoningLevel       the reasoning effort level for reasoning models
     * @param maxActions           maximum number of LLM actions before stopping
     * @param bddInstructions      the BDD instructions to execute
     * @throws IllegalStateException if the API key environment variable is not set
     */
    public LlmMcpAgent(Project project,
            String openaiApiUrl,
            String openaiApiKeyVariable,
            String openaiModel,
            boolean vision,
            String reasoningLevel,
            int maxActions,
            String bddInstructions) {

        String apiKey = System.getenv(openaiApiKeyVariable);
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    "Environment variable '" + openaiApiKeyVariable + "' is not set or is empty.");
        }

        // Initialize the LLM provider using the Strategy pattern
        this.aiProvider = new OpenAiProvider(
                openaiApiUrl,
                apiKey,
                openaiModel,
                vision,
                reasoningLevel);

        this.maxActions = maxActions;
        this.bddInstructions = bddInstructions;

        // Wrap the technical MCP driver with the BDD steps validator
        PlaywrightMcpDriver mcpDriver = new PlaywrightMcpDriver(project, bddInstructions);
        this.mcpInterface = new BddMcpValidator(mcpDriver, new BddStepTracker(bddInstructions));
    }

    /**
     * Constructs a new LlmMcpAgent with a custom LLM provider.
     * 
     * <p>
     * This constructor allows injecting any {@link LlmProvider} implementation,
     * enabling support for different LLM backends (Gemini, Llama, etc.).
     * </p>
     * 
     * @param project         the INGenious project context
     * @param aiProvider      the LLM provider to use
     * @param maxActions      maximum number of LLM actions before stopping
     * @param bddInstructions the BDD instructions to execute
     */
    public LlmMcpAgent(Project project,
            LlmProvider aiProvider,
            int maxActions,
            String bddInstructions) {

        this.aiProvider = aiProvider;
        this.maxActions = maxActions;
        this.bddInstructions = bddInstructions;

        // Wrap the technical MCP driver with the BDD steps validator
        PlaywrightMcpDriver mcpDriver = new PlaywrightMcpDriver(project, bddInstructions);
        this.mcpInterface = new BddMcpValidator(mcpDriver, new BddStepTracker(bddInstructions));
    }

    /**
     * Executes the LLM agent loop to complete the BDD test scenario.
     * 
     * <p>
     * The agent iteratively:
     * </p>
     * <ol>
     * <li>Sends the current conversation state to the LLM</li>
     * <li>Receives tool calls from the LLM</li>
     * <li>Executes the requested tools via {@link McpInterface}</li>
     * <li>Adds tool results to the conversation</li>
     * <li>Repeats until completion or maxActions reached</li>
     * </ol>
     * 
     * @return a status message indicating how the agent terminated
     */
    public String runLLMAgent() {
        // Register the available tools with the LLM provider
        final List<Map<String, Object>> tools = McpToolBuilder.from(McpInterface.class);
        aiProvider.registerTools(tools);

        final McpToolExecutor<McpInterface> executor = McpToolExecutor.of(McpInterface.class, mcpInterface, mapper);

        // This is the "Split + Verbose" strategy base prompt
        final String baseSystemPrompt = "You are a strict BDD-GUI test strategy agent for INGenious MCP.\n" +
                "You must analyze the User Context (DOM state) to perform actions on the GUI step by step.\n" +
                "Your objective is to complete the BDD instructions provided.\n\n" +
                "CRITICAL INSTRUCTION - SPLIT + VERBOSE STRATEGY:\n" +
                "You MUST return your response ONLY as a valid JSON object raw (no markdown backticks). " +
                "Your JSON MUST contain exactly two top-level keys: \"thought\" and \"action\".\n" +
                "- \"thought\": A verbose explanation of your analysis of the current GUI state, why you chose the web element, and how it aligns with the current BDD step.\n"
                +
                "- \"action\": A JSON object describing the tool to execute. It must contain \"toolName\" (string) and \"parameters\" (an object with key-value pairs matching the exact arguments for the chosen tool).\n\n"
                +
                "Example Format:\n" +
                "{\n" +
                "  \"thought\": \"I am observing the DOM. I see the 'Login' button with CSS selector '#login'. Since the BDD says 'When the user logs in', I will click this button.\",\n"
                +
                "  \"action\": {\n" +
                "    \"toolName\": \"executeClickAction\",\n" +
                "    \"parameters\": {\n" +
                "      \"bddStep\": \"When the user logs in\",\n" +
                "      \"rawCssSelector\": \"#login\"\n" +
                "    }\n" +
                "  }\n" +
                "}\n\n" +
                "Use ONLY the tools provided to you. If you complete all asserts or test execution must end, use the stopTestExecution tool. Begin!";

        int step = 0;

        // At the beginning, the context is the BDD list itself
        String currentUserContext = "Initial state. BDD Instructions to accomplish:\n" + this.bddInstructions
                + "\nStart by loading the URL.";

        while (step < this.maxActions) {
            try {
                addInfoLog("\n--- INFERENCING STEP: " + step + " ---");

                // Track Latency for RQ3 metric
                long startTime = System.currentTimeMillis();

                // Call the LLM provider (Strategy pattern)
                String jsonResponse = aiProvider.executePrompt(baseSystemPrompt, currentUserContext);

                long endTime = System.currentTimeMillis();
                long latencyMs = endTime - startTime;

                addInfoLog(String.format("LLM Inference Latency: %d ms (Provider: %s)", latencyMs,
                        aiProvider.getModelName()));

                // Parse the response based on the Split + Verbose schema
                Map<?, ?> parsedResponse;
                try {
                    parsedResponse = mapper.readValue(jsonResponse, Map.class);
                } catch (JsonProcessingException e) {
                    addSevereLog("The LLM failed to return a proper JSON. Attempting recovery...");
                    addSevereLog("Raw Output: " + jsonResponse);
                    currentUserContext = "ERROR: Your last response was not valid JSON. You MUST return ONLY a JSON object with 'thought' and 'action' keys. Try again.";
                    continue;
                }

                String thought = (String) parsedResponse.get("thought");
                Map<?, ?> action = (Map<?, ?>) parsedResponse.get("action");

                if (action == null || !action.containsKey("toolName")) {
                    String feedback = "ISSUE: No 'action' block or 'toolName' provided. Please review.";
                    addSevereLog(feedback);
                    currentUserContext = "Error: Invalid JSON schema. 'action' block missing or 'toolName' empty. Follow strictly the format.";
                    continue;
                }

                String toolName = (String) action.get("toolName");
                Map<?, ?> paramsMap = (Map<?, ?>) action.get("parameters");
                String argumentsJson = mapper.writeValueAsString(paramsMap != null ? paramsMap : new HashMap<>());

                addInfoLog("THOUGHT: " + thought);
                addInfoLog("ACTION: " + toolName);
                addInfoLog("PARAMETERS: " + argumentsJson);

                Object resultObj = executor.execute(toolName, argumentsJson);
                String result = (resultObj == null) ? "null" : resultObj.toString();

                if (McpNames.of(McpInterface::stopTestExecution).equals(toolName)) {
                    addInfoLog("LLM agent decided to stop the test execution.");
                    return "LLM agent decided to stop the test execution";
                }

                boolean requireStateImage = McpNames.of(McpInterface::getStateImage).equals(toolName);

                if (requireStateImage && !result.isEmpty() && aiProvider.supportsVision()) {
                    addInfoLog("VISION REQUEST: Model supports vision, obtaining image frame...");
                    currentUserContext = "Action executing " + toolName
                            + " resulted in: [Base64 Image Attached]. Use the returned DOM and state interactive widgets to continue the BDD.";
                } else {
                    addInfoLog("DEBUG tool result: " + result);
                    // Next user text will be the outcome of the tool or the DOM state
                    currentUserContext = "Result of the previous action '" + toolName + "':\n" + result
                            + "\nAnalyze this consequence and plan your next action to follow the BDD.";
                }

                // Append general BDD instructions reminder to the context loop
                currentUserContext += "\n\nPending BDD:\n" + this.bddInstructions;

                step++;

            } catch (LlmProviderException e) {
                if (e.isRateLimited()) {
                    long waitTime = e.getRetryAfterMs();
                    addInfoLog("Rate limited... waiting " + (waitTime / 1000) + " seconds...");
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return "Execution interrupted";
                    }
                } else {
                    addSevereLog("LLM provider error: " + e.getMessage());
                    return "Stop execution due to LLM provider error: " + e.getHttpStatusCode();
                }
            } catch (Exception e) {
                addSevereLog("LLM step failed: " + e.getMessage());
                e.printStackTrace();
            }
        }

        mcpInterface.stopTestExecution();
        addInfoLog("maxAction executed");
        return "maxAction executed";
    }

    private void addInfoLog(String msg) {
        LOGGER.log(Level.INFO, msg);
    }

    private void addSevereLog(String msg) {
        LOGGER.log(Level.SEVERE, msg);
    }
}
