package com.datatalk.datatalkai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses the model's text answer into an {@link AiReportResponse}.
 * Models sometimes wrap JSON in markdown fences or add reasoning blocks, so those are stripped first.
 */
public final class AiResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AiResponseParser() {
    }

    public static AiReportResponse parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AiException("The AI service returned an empty answer.");
        }
        String text = raw.replaceAll("(?s)<think>.*?</think>", "").trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new AiException("The AI service did not return a JSON report definition.");
        }
        try {
            JsonNode node = MAPPER.readTree(text.substring(start, end + 1));
            String sql = text(node, "sql");
            boolean answerable = node.has("answerable") ? node.get("answerable").asBoolean(true) : sql != null;
            if (sql != null) {
                sql = sql.trim();
                if (sql.isEmpty()) {
                    sql = null;
                }
            }
            if (answerable && sql == null) {
                answerable = false;
            }
            return new AiReportResponse(
                    answerable,
                    text(node, "understanding"),
                    sql,
                    limit(text(node, "title"), 200),
                    limit(text(node, "explanation"), 1000),
                    AiReportResponse.normalizeChartType(text(node, "chartType")),
                    limit(text(node, "clarification"), 1000));
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("The AI service returned invalid JSON.", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String limit(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
