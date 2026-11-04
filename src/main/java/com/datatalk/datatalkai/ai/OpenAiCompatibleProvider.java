package com.datatalk.datatalkai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Provider for any OpenAI-compatible /chat/completions API (OpenAI, NVIDIA API catalog, ...).
 */
public class OpenAiCompatibleProvider implements AiProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String name;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final boolean jsonMode;
    private final Duration readTimeout;
    private final HttpClient http;
    private final int maxTokens;
    private final String reasoningEffort;

    public OpenAiCompatibleProvider(String name, String baseUrl, String apiKey, String model,
                                    boolean jsonMode, int connectTimeoutSeconds, int readTimeoutSeconds, String trustStoreType) {
        this(name, baseUrl, apiKey, model, jsonMode, connectTimeoutSeconds, readTimeoutSeconds, trustStoreType, 2048, null);
    }

    /**
     * @param maxTokens       max completion tokens (keep low for free-tier token limits)
     * @param reasoningEffort optional "low" / "medium" / "high" for reasoning models (e.g. Groq gpt-oss); null = not sent
     */
    public OpenAiCompatibleProvider(String name, String baseUrl, String apiKey, String model,
                                    boolean jsonMode, int connectTimeoutSeconds, int readTimeoutSeconds, String trustStoreType,
                                    int maxTokens, String reasoningEffort) {
        this.name = name;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.jsonMode = jsonMode;
        this.maxTokens = maxTokens;
        this.reasoningEffort = reasoningEffort == null || reasoningEffort.isBlank() ? null : reasoningEffort.trim();
        this.readTimeout = Duration.ofSeconds(readTimeoutSeconds);
        this.http = AiHttpClients.create(connectTimeoutSeconds, trustStoreType);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (!isConfigured()) {
            throw new AiException("AI provider '" + name + "' is not configured (missing API key environment variable).");
        }
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0);
        body.put("max_tokens", maxTokens);
        body.put("stream", false);
        if (reasoningEffort != null) {
            body.put("reasoning_effort", reasoningEffort);
        }
        if (jsonMode) {
            body.putObject("response_format").put("type", "json_object");
        }
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(readTimeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new AiException("AI service returned HTTP " + response.statusCode() + summarize(response.body()));
            }
            JsonNode json = MAPPER.readTree(response.body());
            JsonNode content = json.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
                throw new AiException("AI service returned no content.");
            }
            return content.asText();
        } catch (HttpTimeoutException e) {
            throw new AiException("AI service timed out after " + readTimeout.toSeconds() + " seconds.", e);
        } catch (IOException e) {
            throw new AiException("Could not reach the AI service: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("AI request was interrupted.", e);
        }
    }

    static String summarize(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String s = body.replaceAll("\\s+", " ");
        return ": " + (s.length() > 200 ? s.substring(0, 200) + "..." : s);
    }
}
