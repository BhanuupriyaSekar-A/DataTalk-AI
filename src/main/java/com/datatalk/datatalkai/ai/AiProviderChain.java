package com.datatalk.datatalkai.ai;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Ordered list of AI providers: the primary first, then fallbacks.
 * When a provider fails (timeout, HTTP error, unusable answer) the next one is tried.
 * The result records which provider/model actually answered so the UI can show it.
 */
@Slf4j
public class AiProviderChain {

    /** Successful answer plus the provider that produced it and the providers that failed before it. */
    public record Completion<T>(T value, String provider, String model, List<String> failures) {
        public String label() {
            return provider + ":" + model;
        }

        public boolean usedFallback() {
            return !failures.isEmpty();
        }
    }

    private final List<AiProvider> providers;

    public AiProviderChain(List<AiProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public List<AiProvider> providers() {
        return providers;
    }

    /** First configured provider, used as the "expected" provider label. */
    public String primaryLabel() {
        return providers.stream().filter(AiProvider::isConfigured).findFirst()
                .map(p -> p.name() + ":" + p.model()).orElse("none");
    }

    /**
     * Sends the prompt to each configured provider in order until one returns an answer the parser accepts.
     *
     * @throws AiException when no provider is configured or all providers fail
     */
    public <T> Completion<T> complete(String systemPrompt, String userPrompt, Function<String, T> parser) {
        List<String> failures = new ArrayList<>();
        for (AiProvider provider : providers) {
            if (!provider.isConfigured()) {
                continue;
            }
            String label = provider.name() + ":" + provider.model();
            try {
                T value = parser.apply(provider.complete(systemPrompt, userPrompt));
                if (!failures.isEmpty()) {
                    log.info("AI fallback used: {} answered after failures {}", label, failures);
                }
                return new Completion<>(value, provider.name(), provider.model(), List.copyOf(failures));
            } catch (AiException e) {
                log.warn("AI provider {} failed: {}", label, e.getMessage());
                failures.add(label + " - " + e.getMessage());
            }
        }
        if (failures.isEmpty()) {
            throw new AiException("No AI provider is configured. Set NVIDIA_API_KEY, GEMINI_API_KEY or OPENAI_API_KEY.");
        }
        throw new AiException(String.join(" | ", failures));
    }

    /** Safe description of the chain for the UI (never includes API keys). */
    public List<Map<String, Object>> describe() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < providers.size(); i++) {
            AiProvider p = providers.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("order", i + 1);
            m.put("role", i == 0 ? "primary" : "fallback");
            m.put("provider", p.name());
            m.put("model", p.model());
            m.put("configured", p.isConfigured());
            list.add(m);
        }
        return list;
    }
}
