package com.datatalk.datatalkai.reports;

import java.util.List;

/**
 * A predefined report loaded from src/main/resources/predefined-reports/{category}/*.sql.
 * Reports are used both as runnable reports and as reference examples for the AI prompt.
 */
public record PredefinedReport(String id,
                               String category,
                               String name,
                               String description,
                               List<Parameter> parameters,
                               List<String> examples,
                               String chartType,
                               String sqlTemplate) {

    /** Integer report parameter, referenced in SQL as :name. */
    public record Parameter(String name, String type, int defaultValue, String description) {
    }
}
