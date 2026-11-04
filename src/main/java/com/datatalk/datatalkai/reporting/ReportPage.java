package com.datatalk.datatalkai.reporting;

import java.util.List;

/**
 * One page of real query results.
 *
 * @param columnTypes "number", "date" or "text" for each column (used by the table/chart UI)
 */
public record ReportPage(List<String> columns,
                         List<String> columnTypes,
                         List<List<Object>> rows,
                         long totalRows,
                         int page,
                         int pageSize,
                         int totalPages) {
}
