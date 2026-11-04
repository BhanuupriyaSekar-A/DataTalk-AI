package com.datatalk.datatalkai.ai;

/**
 * A replaceable AI chat-completion provider (OpenAI, NVIDIA, Gemini, ...).
 * Implementations only send the prompt text - never database credentials or raw business data.
 */
public interface AiProvider {

    /** Short provider id, e.g. "openai", "nvidia", "gemini". */
    String name();

    /** Model identifier used for requests. */
    String model();

    /** True when an API key is configured. */
    boolean isConfigured();

    /**
     * Sends a system + user prompt and returns the raw text answer.
     *
     * @throws AiException when the API is unreachable, times out or returns an error
     */
    String complete(String systemPrompt, String userPrompt);
}
