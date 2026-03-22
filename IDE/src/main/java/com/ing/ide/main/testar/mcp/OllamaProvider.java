package com.ing.ide.main.testar.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Local Ollama Provider for the Multi-LLM test architecture.
 */
public class OllamaProvider implements LlmProvider {

    private static final Logger LOGGER = Logger.getLogger(OllamaProvider.class.getName());

    private final String apiUrl;
    private final String model;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private List<Map<String, Object>> registeredTools;

    public OllamaProvider(String apiUrl, String model) {
        this.apiUrl = apiUrl != null && !apiUrl.isBlank() ? apiUrl : "http://localhost:11434/api/generate";
        this.model = model != null && !model.isBlank() ? model : "llama3.1";

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String executePrompt(String systemPrompt, String userContext) throws LlmProviderException {
        try {
            String fullPrompt = systemPrompt + "\n\nUser Context:\n" + userContext;

            if (registeredTools != null && !registeredTools.isEmpty()) {
                String toolsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(registeredTools);
                // We instruct Ollama specifically locally about what tools to use for output
                // JSON format
                fullPrompt += "\n\nAVAILABLE TOOLS:\n" + toolsJson;
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", this.model);
            requestBody.put("prompt", fullPrompt);
            requestBody.put("stream", false);

            // Force JSON output
            requestBody.put("format", "json");

            String requestBodyJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.apiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                LOGGER.log(Level.SEVERE, "Ollama API Error. Status: {0}, Body: {1}",
                        new Object[] { response.statusCode(), response.body() });
                throw new LlmProviderException("Ollama API Error: " + response.statusCode(), response.statusCode(),
                        false, 0);
            }

            Map<?, ?> responseMap = objectMapper.readValue(response.body(), Map.class);
            String responseText = (String) responseMap.get("response");

            if (responseText != null) {
                return responseText.trim();
            }

            throw new LlmProviderException("Could not extract response from Ollama API");

        } catch (IOException | InterruptedException e) {
            throw new LlmProviderException("Communication error with Ollama API", e);
        }
    }

    @Override
    public void registerTools(List<Map<String, Object>> tools) {
        this.registeredTools = tools;
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public boolean supportsVision() {
        return model.contains("llava");
    }

    @Override
    public boolean isReasoningModel() {
        return false;
    }
}
