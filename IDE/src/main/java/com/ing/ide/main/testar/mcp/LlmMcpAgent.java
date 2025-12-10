package com.ing.ide.main.testar.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ing.datalib.component.Project;
import com.ing.ide.main.testar.mcp.helper.McpNames;
import com.ing.ide.main.testar.mcp.helper.McpToolBuilder;
import com.ing.ide.main.testar.mcp.helper.McpToolExecutor;
import okhttp3.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class LlmMcpAgent {

    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    private final String openaiApiUrl;
    private final String openaiApiKey;
    private final String openaiModel;
    private final boolean vision;
    private final String reasoningLevel;
    private final int maxActions;
    private final String bddInstructions;

    private final McpInterface mcpInterface;

    public LlmMcpAgent(Project project,
                       String openaiApiUrl,
                       String openaiApiKeyVariable,
                       String openaiModel,
                       boolean vision,
                       String reasoningLevel,
                       int maxActions,
                       String bddInstructions
                      ) {

        this.openaiApiUrl = openaiApiUrl;
        this.openaiApiKey = System.getenv(openaiApiKeyVariable);
        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            throw new IllegalStateException("Environment variable '" + openaiApiKeyVariable + "' is not set or is empty.");
        }
        this.openaiModel = openaiModel;
        this.vision = vision;
        this.reasoningLevel = reasoningLevel;
        this.maxActions = maxActions;
        this.bddInstructions = bddInstructions;

        // Wrap the technical MCP driver with the BDD steps validator
        PlaywrightMcpDriver mcpDriver = new PlaywrightMcpDriver(project);
        this.mcpInterface = new BddMcpValidator(mcpDriver, new BddStepTracker(bddInstructions));

        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.MINUTES)
                .retryOnConnectionFailure(true)
                .build();
    }

    public String runLLMAgent() {
        // Prepare the messages and tools data to be sent to the LLM
        final List<Map<String, Object>> messages = defineMessages();
        final List<Map<String, Object>> tools = McpToolBuilder.from(McpInterface.class);
        final McpToolExecutor<McpInterface> executor = McpToolExecutor.of(McpInterface.class, mcpInterface, mapper);

        int step = 0;
        boolean visionRequest = false; // this (sticky) flag indicates vision should be enabled

        while (step < this.maxActions) {
            Map<String, Object> body = new HashMap<>();

            // modes, tools, tool_choice, reasoning_effort first as they are 'static' content (reusing the KV cache)
            body.put("model", openaiModel);
            body.put("tools", tools);
            body.put("tool_choice", "auto");
            if (isReasoningModel(openaiModel)) {
                body.put("reasoning_effort", reasoningLevel);
            }

            // put messages last as this is 'variable' content
            body.put("messages", messages);

            try (Response response = client.newCall(
                    new Request.Builder()
                            .url(openaiApiUrl)
                            .header("Authorization", "Bearer " + openaiApiKey)
                            .header("Content-Type", "application/json")
                            .header("Copilot-Vision-Request", "" + visionRequest)
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
                                addSevereLog("Invalid Retry-After header value: " + retryAfter);
                            }
                        }

                        addInfoLog("OpenAI rate limited (429)... wait " + (waitTime / 1000) + " seconds...");
                        Thread.sleep(waitTime);
                    } else {
                        String failed = "Stop execution due to OpenAI call fail: " + response.code();
                        addSevereLog(failed);
                        try {
                            String request_body = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(body);
                            addSevereLog("request headers: " + response.request().headers().toString());
                            addSevereLog("request body: " + request_body);
                            addSevereLog("response: " + response.body().string());

                            return failed;
                        } catch (JsonProcessingException e) {
                            addSevereLog("JSON processing failed" + e.getMessage());
                        }
                    }
                } else {
                    List<Map<String, Object>> toolCallResultMessages = new ArrayList<>();
                    List<Map<String, Object>> userMessages = new ArrayList<>();

                    // if response is successful
                    String json = Objects.requireNonNull(response.body()).string();

                    Map<?, ?> parsed = mapper.readValue(json, Map.class);
                    logTokenUsage((Map<?, ?>) parsed.get("usage"));
                    Map<?, ?> choice = ((List<Map<?, ?>>) parsed.get("choices")).get(0);
                    Map<?, ?> message = (Map<?, ?>) choice.get("message");

                    messages.add((Map<String, Object>) message);

                    // Read all tool_calls (array)
                    List<Map<?, ?>> toolCalls = (List<Map<?, ?>>) message.get("tool_calls");

                    // Empty toolCalls response. Don't stop, give the LLM another chance
                    if (toolCalls == null || toolCalls.isEmpty()) {
                        String feedback = "ISSUE: No tool was selected. Please review the last results.";
                        addInfoLog(feedback);
                        userMessages.add(Map.of(
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
                        String argumentsJson = (String) function.get("arguments");

                        addInfoLog("DEBUG toolName: " + toolName);
                        addInfoLog("DEBUG argumentsJson: " + argumentsJson);

                        Object resultObj = executor.execute(toolName, argumentsJson);
                        String result = (resultObj == null) ? "null" : resultObj.toString();

                        // Check if stop the execution due to the LLM decision
                        if (McpNames.of(McpInterface::stopTestExecution).equals(toolName)) {
                            addInfoLog("LLM agent decided to stop the test execution");
                            return "LLM agent decided to stop the test execution";
                        }

                        // Reply to this tool call first
                        boolean requireStateImage = McpNames.of(McpInterface::getStateImage).equals(toolName);
                        String toolContent = requireStateImage ? "screenshot_ready" : result;
                        toolCallResultMessages.add(Map.of(
                                "role", "tool",
                                "tool_call_id", callId,
                                "content", toolContent
                        ));

                        // If required, additionally add the image user message
                        if (requireStateImage && !result.isEmpty() && supportsVision(openaiModel)) {
                            addInfoLog("VISION REQUEST: attaching state image");
                            attachStateImage(userMessages, result);
                            visionRequest = true;
                        } else if (requireStateImage && !result.isEmpty()) {
                            addInfoLog("VISION REQUEST: is omitted for this model or by the user");
                            userMessages.add(Map.of(
                                    "role", "user",
                                    "content", "Screenshot captured (omitted for this model)."
                            ));
                            // add vision request for the next iteration
                        } else {
                            // This is only for debugging purposes
                            addInfoLog("DEBUG result: " + result);
                        }
                    }

                    // Tool results first
                    messages.addAll(toolCallResultMessages);
                    // User messages second
                    messages.addAll(userMessages);

                    // step if there are no exceptions
                    step++;
                }
            } catch (Exception e) {
                try {
                    String request = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(body);
                    addSevereLog("LLM step failed: " + e.getMessage());
                    addSevereLog("request: " + request);
                    e.printStackTrace();
                } catch (JsonProcessingException ex) {
                    addSevereLog("JSON processing failed" + ex.getMessage());
                }

            }
        }

        mcpInterface.stopTestExecution();
        addInfoLog("maxAction executed");
        return "maxAction executed";
    }

    private List<Map<String, Object>> defineMessages() {
        List<Map<String, Object>> messages = new ArrayList<>();

        messages.add(Map.of(
                "role", "system",
                "content", "You are a BDD-GUI test agent. " +
                        "Your goal is to complete the BDD instructions. " +
                        "Use loadWebURL, getStateInteractiveWidgets, executeClickAction, executeFillAction, and executeSelectAction functions. " +
                        "Use getCurrentURL and checkExecutedActions functions if you need assistance. " +
                        "Use navigateBack function if you need to control the web browser. " +
                        "After completing each BDD step (Given, When, Then), use (getStateImage or getStateVisualText) and addStepAssert functions to validate that step. " +
                        "When asserting all BDD instructions, use the stopTestExecution function.")
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
        return vision && (m.contains("gpt-4o") || m.contains("gpt-4.1") || m.contains("gpt-5"));
    }

    private boolean isReasoningModel(String model) {
        if (model == null) return false;
        String m = model.toLowerCase();
        return m.startsWith("gpt-5");
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

    private void addInfoLog(String msg) {
        java.util.logging.Logger.getLogger(LlmMcpAgent.class.getName()).log(
                java.util.logging.Level.INFO,
                msg
        );
    }

    private void addSevereLog(String msg) {
        java.util.logging.Logger.getLogger(LlmMcpAgent.class.getName()).log(
                java.util.logging.Level.SEVERE,
                msg
        );
    }

    private Number asNumber(Object v) {
        return v instanceof Number ? (Number) v : null;
    }

    private void logTokenUsage(Map<?, ?> usage) {
        if (usage == null || usage.isEmpty()) {
            addInfoLog("DEBUG tokens step: usage not provided by API");
            return;
        }

        // Prompt tokens are the tokens that you input into the model (instructions + history + tool specs).
        Number promptTokens = asNumber(usage.get("prompt_tokens"));
        // Completion tokens are any tokens that the model generates in response to your input.
        Number completionTokens = asNumber(usage.get("completion_tokens"));
        // Sum of prompt + completion (plus any extra accounting fields the API might add). Use it to measure the cost per call.
        Number totalTokens = asNumber(usage.get("total_tokens"));

        addInfoLog("DEBUG tokens step: promptTokens=" + promptTokens +
                ", completionTokens=" + completionTokens +
                ", totalTokens=" + totalTokens);
    }

}
