package com.ing.ide.main.testar.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ollama provider with native tool calling support, designed for models
 * that support the /api/chat endpoint with a {@code tools} field (e.g. Qwen2.5,
 * Qwen3, Llama 3.1+).
 *
 * <p>
 * Unlike {@link OllamaProvider} (which uses {@code /api/generate} and injects
 * tools as plain text in the prompt), this provider uses the structured
 * {@code /api/chat} endpoint and passes tools in the native OpenAI-compatible
 * format that Ollama understands. This results in much more reliable tool
 * calling because the model sees the tools as structured metadata rather than
 * free-text instructions.
 * </p>
 *
 * <h2>Supported models (≤12B, tested or documented)</h2>
 * <ul>
 *   <li>{@code qwen2.5:7b}  — strong tool calling, production-ready</li>
 *   <li>{@code qwen2.5:14b} — best quality if VRAM allows</li>
 *   <li>{@code qwen3:8b}    — newer generation, reasoning + tools</li>
 * </ul>
 *
 * <h2>Architecture note</h2>
 * <p>
 * This class follows the Strategy Pattern defined by {@link LlmProvider} and
 * is registered in {@link LlmProviderFactory} under the key
 * {@code "Qwen/Ollama"}.
 * </p>
 *
 * @author TFG-MCP-TESTAR Team
 * @version 1.0
 * @since 2025
 * @see LlmProvider
 * @see OllamaProvider
 */
public class QwenOllamaProvider implements LlmProvider {

    private static final Logger LOGGER = Logger.getLogger(QwenOllamaProvider.class.getName());

    /** Default base URL for a local Ollama instance. */
    private static final String DEFAULT_API_URL = "http://localhost:11434/api/chat";

    /** Default model — small enough to run on 8 GB VRAM or CPU offload. */
    private static final String DEFAULT_MODEL = "qwen2.5:7b";

    private final String apiUrl;
    private final String model;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /** Tools registered via {@link #registerTools(List)}, forwarded natively. */
    private List<Map<String, Object>> registeredTools;

    /** Native conversation history to enable Qwen to remember past steps and context */
    private final List<Map<String, Object>> messagesHistory = new ArrayList<>();
    private List<Map<String, Object>> lastToolCalls = null;

    // ── Constructor ──────────────────────────────────────────────────────────

    /**
     * Creates a new provider instance.
     *
     * @param apiUrl base URL of the Ollama {@code /api/chat} endpoint;
     *               falls back to {@value #DEFAULT_API_URL} if null/blank
     * @param model  Ollama model tag (e.g. {@code "qwen2.5:7b"});
     *               falls back to {@value #DEFAULT_MODEL} if null/blank
     */
    public QwenOllamaProvider(String apiUrl, String model) {
        this.apiUrl  = (apiUrl != null && !apiUrl.isBlank()) ? apiUrl : DEFAULT_API_URL;
        this.model   = (model  != null && !model.isBlank())  ? model  : DEFAULT_MODEL;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // ── LlmProvider API ──────────────────────────────────────────────────────

    @Override
    public void registerTools(List<Map<String, Object>> tools) {
        this.registeredTools = tools;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Sends a {@code /api/chat} request to Ollama with the system prompt,
     * user context and, when available, the registered tools in native format.
     * The response is extracted from {@code message.content}.
     * </p>
     *
     * <p>
     * If the model returns a {@code tool_calls} block instead of plain content
     * (i.e. it decided to call a tool), the tool call is serialised back to
     * JSON so that the upstream {@link LlmMcpAgent} can parse it with its
     * existing JSON-based dispatcher — keeping the rest of the pipeline
     * unchanged.
     * </p>
     */
    @Override
    public String executePrompt(String systemPrompt, String userContext) throws LlmProviderException {

        // ── 1. Clean the LlmMcpAgent's manual history string ───────────────────
        // LlmMcpAgent concatenates the entire action history manually for stateless providers.
        // Since we are handling conversation history natively in an array, we must strip that 
        // stringified history off to prevent duplicating Context text exponentially.
        String cleanUserContext = userContext;
        int histIdx = cleanUserContext.indexOf("\n\nPast Action History:\n");
        if (histIdx != -1) {
            cleanUserContext = cleanUserContext.substring(0, histIdx).trim();
            // Just a short reminder to keep the model focused
            cleanUserContext += "\n\n(Follow the Pending BDD instructions from your first prompt)";
        }

        // ── 2. Update the messages history ─────────────────────────────────────
        if (messagesHistory.isEmpty()) {
            messagesHistory.add(Map.of("role", "system", "content", systemPrompt));
            messagesHistory.add(Map.of("role", "user", "content", cleanUserContext));
        } else {
            // Append as a tool response if the model invoked tool calls in the last step
            if (lastToolCalls != null && !lastToolCalls.isEmpty()) {
                Map<String, Object> toolMsg = new HashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("content", cleanUserContext);
                
                messagesHistory.add(toolMsg);
            } else {
                // Otherwise normal user message (e.g. error recoveries)
                messagesHistory.add(Map.of("role", "user", "content", cleanUserContext));
            }
        }
        
        lastToolCalls = null;

        // ── 3. Build the request body ─────────────────────────────────────────
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model",    this.model);
        requestBody.put("messages", messagesHistory);
        requestBody.put("stream",   false);

        // Pass tools natively — this is the key difference vs OllamaProvider
        if (registeredTools != null && !registeredTools.isEmpty()) {
            requestBody.put("tools", registeredTools);
        }

        // ── 3. Serialise and send ─────────────────────────────────────────────
        String requestBodyJson;
        try {
            requestBodyJson = objectMapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException e) {
            throw new LlmProviderException("Failed to serialize Qwen/Ollama request body", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.apiUrl))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new LlmProviderException("Communication error with Qwen/Ollama API", e);
        }

        // ── 4. Handle HTTP errors ─────────────────────────────────────────────
        if (response.statusCode() >= 400) {
            LOGGER.log(Level.SEVERE, "Qwen/Ollama API error. Status: {0}, Body: {1}",
                    new Object[]{ response.statusCode(), response.body() });
            throw new LlmProviderException(
                    "Qwen/Ollama API error: " + response.statusCode(),
                    response.statusCode(), false, 0);
        }

        // ── 5. Parse the response ─────────────────────────────────────────────
        try {
            Map<?, ?> parsed = objectMapper.readValue(response.body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) parsed.get("message");

            if (message == null) {
                throw new LlmProviderException("Qwen/Ollama response missing 'message' field. Body: "
                        + response.body());
            }

            // Always add the raw assistant message to the chat history so it remembers
            messagesHistory.add(message);

            // 5a. Native tool_calls path  ─────────────────────────────────────
            // Some Qwen versions return tool_calls instead of plain content
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                this.lastToolCalls = toolCalls;
                Map<String, Object> firstCall = toolCalls.get(0);
                Map<?, ?> function  = (Map<?, ?>) firstCall.get("function");
                if (function != null) {
                    String name      = (String) function.get("name");
                    Object arguments = function.get("arguments");

                    // Build the nested action object the LlmMcpAgent expects:
                    // { "toolName": "...", "parameters": { ... } }
                    Map<String, Object> actionObj = new HashMap<>();
                    actionObj.put("toolName", name);
                    actionObj.put("parameters",
                            arguments instanceof Map ? arguments : new HashMap<>());

                    Map<String, Object> agentJson = new HashMap<>();
                    agentJson.put("thought",  "Calling tool: " + name);
                    agentJson.put("action",   actionObj);   // OBJECT, not a String

                    String result = objectMapper.writeValueAsString(agentJson);
                    LOGGER.log(Level.INFO,
                            "[QwenOllamaProvider] Native tool_call detected: {0} → {1}",
                            new Object[]{ name, result });
                    return result;
                }
            }

            // 5b. Plain content path  ─────────────────────────────────────────
            String content = (String) message.get("content");
            if (content == null || content.isBlank()) {
                throw new LlmProviderException(
                        "Qwen/Ollama returned empty content and no tool_calls. Body: " + response.body());
            }

            // Strip markdown code fences if present (```json … ```)
            content = content.trim();
            if (content.startsWith("```json")) content = content.substring(7);
            if (content.startsWith("```"))     content = content.substring(3);
            if (content.endsWith("```"))       content = content.substring(0, content.length() - 3);

            // Strip Qwen3 <think>…</think> reasoning blocks if present
            content = stripThinkingBlock(content.trim());

            LOGGER.log(Level.INFO,
                    "[QwenOllamaProvider] Content response (first 200 chars): {0}",
                    content.substring(0, Math.min(200, content.length())));

            return content.trim();

        } catch (JsonProcessingException e) {
            throw new LlmProviderException("Failed to parse Qwen/Ollama JSON response", e);
        }
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public boolean supportsVision() {
        // qwen2.5-vl and qwen-vl variants support vision
        return model.toLowerCase().contains("vl");
    }

    @Override
    public boolean isReasoningModel() {
        // qwen3 series supports "thinking" mode
        return model.toLowerCase().startsWith("qwen3");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Removes Qwen3-style {@code <think>…</think>} blocks that appear before
     * the actual JSON response when the model runs in "thinking" mode.
     */
    private String stripThinkingBlock(String text) {
        if (text == null) return "";
        // Remove <think>...</think> (can span multiple lines)
        String stripped = text.replaceAll("(?s)<think>.*?</think>", "").trim();
        return stripped.isEmpty() ? text : stripped;
    }
}
