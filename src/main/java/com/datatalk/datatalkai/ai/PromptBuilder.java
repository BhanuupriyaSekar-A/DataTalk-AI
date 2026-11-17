package com.datatalk.datatalkai.ai;

import com.datatalk.datatalkai.reporting.ReportingCatalog;
import com.datatalk.datatalkai.reporting.UserScope;

import java.time.LocalDate;
import java.util.List;

/**
 * Builds the AI prompt from approved schema metadata, predefined report examples and compact context.
 * Only table/column descriptions and the user's question are sent - no credentials, no row data,
 * no customer names or ids.
 */
public final class PromptBuilder {

    private PromptBuilder() {
    }

    /** A previous turn reused for follow-up questions. */
    public record PreviousTurn(String question, String title, String sql) {
    }

    /** A predefined report rendered with default parameters, used as a reference example. */
    public record Example(String name, String description, String question, String sql) {
    }

    public static String systemPrompt(ReportingCatalog catalog, UserScope scope, List<Example> examples) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are SL Talk AI, a PostgreSQL reporting assistant for a cashless payment and device management company.
                Convert the user's business question into ONE read-only PostgreSQL SELECT query using ONLY the tables
                and columns listed below. Answer with a single JSON object and nothing else:
                {"answerable": true|false, "understanding": "...", "sql": "...", "title": "...",
                 "explanation": "...", "chartType": "table|bar|line|pie", "clarification": "..."}

                Rules:
                - Use only the listed tables, always schema-qualified (e.g. txn.payment_transaction), with table aliases.
                - A single SELECT statement (WITH ... SELECT is allowed). No INSERT/UPDATE/DELETE/DDL, no semicolons inside.
                - Do NOT filter by customer_id for the current user and do NOT add LIMIT: data access, customer scope
                  and row limits are enforced automatically by the application.
                - Prefer aggregated, human-readable results (names instead of ids) with clear column aliases in snake_case.
                - Always add ORDER BY. Use date_trunc for daily/weekly/monthly grouping and cast to ::date for days.
                - Relative dates use CURRENT_DATE (e.g. transaction_time >= CURRENT_DATE - INTERVAL '30 days').
                - "failed" payments means transaction_status IN ('DECLINED','FAILED','REFUNDED') unless stated otherwise.
                - "sales"/"revenue" means SUM(amount) of APPROVED transactions.
                - chartType: line for time series, bar for category comparisons, pie for share of total (<= 8 slices),
                  table for detailed lists.
                - If the question cannot be answered with these tables (or asks for passwords, users, card numbers,
                  personal data or system information), return "answerable": false, "sql": null and a short
                  "clarification" explaining what can be asked instead.
                """);
        sb.append("\nToday's date: ").append(LocalDate.now()).append('\n');
        sb.append(scope.isAdmin()
                ? "The user is an ADMIN and may report across all customers (join corp.customer for customer names).\n"
                : "The user is a " + scope.role() + " user; results are automatically restricted to their own company"
                + (scope.allowedDeviceIds() != null ? " and their assigned devices" : "") + ".\n");
        sb.append("\nApproved tables and columns:\n").append(catalog.describe(scope.allowedTables()));
        if (!examples.isEmpty()) {
            sb.append("\nReference report examples (style guide, not training data):\n");
            for (Example e : examples) {
                sb.append("-- ").append(e.name()).append(": ").append(e.description()).append('\n');
                if (e.question() != null) {
                    sb.append("-- Question: ").append(e.question()).append('\n');
                }
                sb.append(e.sql()).append("\n\n");
            }
        }
        return sb.toString();
    }

    public static String userPrompt(String question, List<PreviousTurn> previousTurns) {
        StringBuilder sb = new StringBuilder();
        if (previousTurns != null && !previousTurns.isEmpty()) {
            sb.append("Previous questions in this conversation (use them only if the new question is a follow-up):\n");
            for (PreviousTurn t : previousTurns) {
                sb.append("- Q: ").append(t.question()).append('\n');
                if (t.sql() != null) {
                    sb.append("  SQL: ").append(t.sql().replaceAll("\\s+", " ")).append('\n');
                }
            }
            sb.append('\n');
        }
        sb.append("Question: ").append(question);
        return sb.toString();
    }
}
