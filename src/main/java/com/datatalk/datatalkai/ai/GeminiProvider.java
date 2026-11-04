package com.datatalk.datatalkai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Google Gemini provider (generateContent REST API).
 */
public class GeminiProvider implements AiProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Duration readTimeout;
    private final HttpClient http;

    public GeminiProvider(String baseUrl, String apiKey, String model, int connectTimeoutSeconds, int readTimeoutSeconds, String trustStoreType) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.readTimeout = Duration.ofSeconds(readTimeoutSeconds);
        this.http = AiHttpClients.create(connectTimeoutSeconds, trustStoreType);
    }

    @Override
    public String name() {
        return "gemini";
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
            throw new AiException("AI provider 'gemini' is not configured (missing GEMINI_API_KEY).");
        }
        ObjectNode body = MAPPER.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", systemPrompt);
        body.putArray("contents").addObject().put("role", "user").putArray("parts").addObject().put("text", userPrompt);
        body.putObject("generationConfig").put("temperature", 0).put("responseMimeType", "application/json");

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/models/" + model + ":generateContent"))
                .timeout(readTimeout)
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new AiException("AI service returned HTTP " + response.statusCode()
                        + OpenAiCompatibleProvider.summarize(response.body()));
            }
            JsonNode json = MAPPER.readTree(response.body());
            // Gemini 3 models may return "thought" parts before the answer: keep answer text only
            StringBuilder text = new StringBuilder();
            for (JsonNode part : json.path("candidates").path(0).path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false) && part.hasNonNull("text")) {
                    text.append(part.get("text").asText());
                }
            }
            if (text.toString().isBlank()) {
                throw new AiException("AI service returned no content.");
            }
            return text.toString();
        } catch (HttpTimeoutException e) {
            throw new AiException("AI service timed out after " + readTimeout.toSeconds() + " seconds.", e);
        } catch (IOException e) {
            throw new AiException("Could not reach the AI service: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("AI request was interrupted.", e);
        }
    }
}
