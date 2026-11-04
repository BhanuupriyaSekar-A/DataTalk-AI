package com.datatalk.datatalkai.ai;

import com.datatalk.datatalkai.chat.ChatServiceTestAccess;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiProviderChainTest {

    /** Simple stub provider: returns a fixed answer or throws AiException. */
    private record Stub(String name, String model, boolean isConfigured, String answer) implements AiProvider {
        @Override
        public String complete(String systemPrompt, String userPrompt) {
            if (answer == null) {
                throw new AiException("AI service timed out after 60 seconds.");
            }
            return answer;
        }
    }

    private static final String VALID = "{\"answerable\":true,\"sql\":\"SELECT 1\",\"title\":\"T\",\"chart_type\":\"table\"}";

    @Test
    void primaryAnswersWithoutFallback() {
        AiProviderChain chain = new AiProviderChain(List.of(
                new Stub("nvidia", "deepseek-ai/deepseek-v4.1-flash", true, VALID),
                new Stub("gemini", "gemini-3.8-flash", true, VALID)));
        AiProviderChain.Completion<AiReportResponse> c = chain.complete("s", "u", AiResponseParser::parse);
        assertEquals("nvidia:deepseek-ai/deepseek-v4.1-flash", c.label());
        assertFalse(c.usedFallback());
    }

    @Test
    void fallsBackWhenPrimaryTimesOut() {
        AiProviderChain chain = new AiProviderChain(List.of(
                new Stub("nvidia", "deepseek-ai/deepseek-v4.1-flash", true, null),
                new Stub("gemini", "gemini-3.8-flash", true, VALID)));
        AiProviderChain.Completion<AiReportResponse> c = chain.complete("s", "u", AiResponseParser::parse);
        assertEquals("gemini:gemini-3.8-flash", c.label());
        assertTrue(c.usedFallback());
        assertTrue(c.failures().get(0).startsWith("nvidia:deepseek-ai/deepseek-v4.1-flash"));
    }

    @Test
    void fallsBackWhenAnswerIsNotJson() {
        AiProviderChain chain = new AiProviderChain(List.of(
                new Stub("nvidia", "m1", true, "Sorry, I cannot help"),
                new Stub("gemini", "gemini-3.1-flash-lite", true, VALID)));
        assertEquals("gemini:gemini-3.1-flash-lite", chain.complete("s", "u", AiResponseParser::parse).label());
    }

    @Test
    void skipsUnconfiguredProviders() {
        AiProviderChain chain = new AiProviderChain(List.of(
                new Stub("nvidia", "m1", false, VALID),
                new Stub("gemini", "g1", true, VALID)));
        AiProviderChain.Completion<AiReportResponse> c = chain.complete("s", "u", AiResponseParser::parse);
        assertEquals("gemini:g1", c.label());
        assertFalse(c.usedFallback());
        assertEquals("gemini:g1", chain.primaryLabel());
    }

    @Test
    void allProvidersFailing() {
        AiProviderChain chain = new AiProviderChain(List.of(
                new Stub("nvidia", "m1", true, null),
                new Stub("gemini", "g1", true, null)));
        AiException e = assertThrows(AiException.class, () -> chain.complete("s", "u", AiResponseParser::parse));
        assertTrue(e.getMessage().contains("nvidia:m1") && e.getMessage().contains("gemini:g1"));
    }

    @Test
    void noProviderConfigured() {
        AiProviderChain chain = new AiProviderChain(List.of(new Stub("nvidia", "m1", false, VALID)));
        assertThrows(AiException.class, () -> chain.complete("s", "u", AiResponseParser::parse));
    }

    @Test
    void describeNeverExposesKeys() {
        AiProviderChain chain = new AiProviderChain(List.of(new Stub("nvidia", "m1", true, VALID)));
        assertEquals(List.of("order", "role", "provider", "model", "configured"),
                List.copyOf(chain.describe().get(0).keySet()));
    }

    @Test
    void historyProviderLabelFitsColumn() {
        assertEquals("nvidia:deepseek-v4.1-flash", ChatServiceTestAccess.dbProvider("nvidia:deepseek-ai/deepseek-v4.1-flash"));
        assertEquals("gemini:gemini-3.1-flash-lite", ChatServiceTestAccess.dbProvider("gemini:gemini-3.1-flash-lite"));
        assertTrue(ChatServiceTestAccess.dbProvider("x:" + "m".repeat(50)).length() <= 30);
    }
}
