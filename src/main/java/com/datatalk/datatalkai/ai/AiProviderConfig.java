package com.datatalk.datatalkai.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the AI provider chain: app.ai.provider (primary) followed by app.ai.fallback-providers.
 * Supported providers: groq | openai | nvidia | gemini. A provider's model setting may list several
 * comma-separated models (e.g. GEMINI_MODEL=gemini-3.8-flash,gemini-3.1-flash-lite); each is tried in order.
 * Switching providers only requires changing environment variables.
 */
@Configuration
public class AiProviderConfig {

    @Bean
    public AiProviderChain aiProviderChain(Environment env,
                                           @Value("${app.ai.provider:groq}") String primary,
                                           @Value("${app.ai.fallback-providers:}") String fallbacks,
                                           @Value("${app.ai.connect-timeout-seconds:10}") int connectTimeout,
                                           @Value("${app.ai.read-timeout-seconds:120}") int readTimeout,
                                           @Value("${app.ai.trust-store-type:auto}") String trustStoreType) {
        Set<String> names = new LinkedHashSet<>();
        names.add(primary.trim().toLowerCase());
        for (String f : fallbacks.split(",")) {
            if (!f.isBlank()) {
                names.add(f.trim().toLowerCase());
            }
        }
        List<AiProvider> providers = new ArrayList<>();
        for (String name : names) {
            int timeout = env.getProperty("app.ai." + name + ".read-timeout-seconds", Integer.class, readTimeout);
            for (String model : models(env, name)) {
                providers.add(create(env, name, model, connectTimeout, timeout, trustStoreType));
            }
        }
        return new AiProviderChain(providers);
    }

    private static List<String> models(Environment env, String name) {
        String defaultModel = switch (name) {
            case "gemini" -> "gemini-3.8-flash";
            case "nvidia" -> "deepseek-ai/deepseek-v4.1-flash";
            case "groq" -> "openai/gpt-oss-20b";
            default -> "gpt-4o-mini";
        };
        List<String> models = new ArrayList<>();
        for (String m : env.getProperty("app.ai." + name + ".model", defaultModel).split(",")) {
            if (!m.isBlank()) {
                models.add(m.trim());
            }
        }
        return models.isEmpty() ? List.of(defaultModel) : models;
    }

    private static AiProvider create(Environment env, String name, String model, int connectTimeout,
                                     int readTimeout, String trustStoreType) {
        return switch (name) {
            case "gemini" -> new GeminiProvider(
                    env.getProperty("app.ai.gemini.base-url", "https://generativelanguage.googleapis.com/v1beta"),
                    env.getProperty("app.ai.gemini.api-key", ""),
                    model, connectTimeout, readTimeout, trustStoreType);
            case "openai", "nvidia" -> new OpenAiCompatibleProvider(name,
                    env.getProperty("app.ai." + name + ".base-url"),
                    env.getProperty("app.ai." + name + ".api-key", ""),
                    model, "openai".equals(name), connectTimeout, readTimeout, trustStoreType);
            // Groq (OpenAI-compatible): JSON mode, low reasoning effort and capped output to stay within free-tier TPM
            case "groq" -> new OpenAiCompatibleProvider(name,
                    env.getProperty("app.ai.groq.base-url", "https://api.groq.com/openai/v1"),
                    env.getProperty("app.ai.groq.api-key", ""),
                    model, true, connectTimeout, readTimeout, trustStoreType,
                    env.getProperty("app.ai.groq.max-tokens", Integer.class, 1024),
                    env.getProperty("app.ai.groq.reasoning-effort", "low"));
            default -> throw new IllegalStateException("Unknown AI provider: " + name
                    + " (expected groq, openai, nvidia or gemini)");
        };
    }
}
