package com.ing.ide.main.testar.mcp;

public class LlmProviderFactory {

    public static LlmProvider getProvider(McpAgentSettings settings) {
        String providerName = settings.llmProviderName != null ? settings.llmProviderName : "OpenAI";
        String apiKey = System.getenv(settings.apiKeyEnvVarName);

        switch (providerName) {
            case "Gemini":
                return new GeminiProvider(
                        settings.apiUrl,
                        apiKey,
                        settings.openaiModel);
            case "Local/Ollama":
                return new OllamaProvider(
                        settings.customApiUrl != null && !settings.customApiUrl.isEmpty() ? settings.customApiUrl
                                : "http://localhost:11434/api/generate",
                        settings.openaiModel);
            case "Qwen/Ollama":
                return new QwenOllamaProvider(
                        settings.customApiUrl != null && !settings.customApiUrl.isEmpty() ? settings.customApiUrl
                                : "http://localhost:11434/api/chat",
                        settings.openaiModel);
            case "OpenAI":
            default:
                if (apiKey == null || apiKey.isEmpty()) {
                    throw new IllegalStateException(
                            "Environment variable '" + settings.apiKeyEnvVarName + "' is not set or is empty.");
                }
                return new OpenAiProvider(
                        settings.apiUrl,
                        apiKey,
                        settings.openaiModel,
                        settings.vision != null && settings.vision,
                        settings.reasoningLevel);
        }
    }
}
