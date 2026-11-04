package com.datatalk.datatalkai.ai;

import java.util.Set;

/**
 * Structured answer expected from the AI model.
 *
 * @param answerable    false when the question cannot be answered from the approved data
 * @param understanding the AI's restatement of the question
 * @param sql           a single PostgreSQL SELECT (validated by Java before execution)
 * @param title         short report title
 * @param explanation   one or two sentences describing the report
 * @param chartType     table | bar | line | pie
 * @param clarification message for the user when answerable is false
 */
public record AiReportResponse(boolean answerable,
                               String understanding,
                               String sql,
                               String title,
                               String explanation,
                               String chartType,
                               String clarification) {

    public static final Set<String> CHART_TYPES = Set.of("table", "bar", "line", "pie");

    public static String normalizeChartType(String type) {
        if (type == null) {
            return "table";
        }
        String t = type.trim().toLowerCase();
        return CHART_TYPES.contains(t) ? t : "table";
    }
}
