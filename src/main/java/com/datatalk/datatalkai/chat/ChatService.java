package com.datatalk.datatalkai.chat;

import com.datatalk.datatalkai.ai.*;
import com.datatalk.datatalkai.reporting.*;
import com.datatalk.datatalkai.reports.PredefinedReport;
import com.datatalk.datatalkai.reports.PredefinedReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Orchestrates a natural-language question:
 * question -> (cached SQL | AI) -> Java SQL validation + permission scope -> read-only execution -> history.
 * The AI never decides data access: the user scope comes from the session and the database.
 */
@Slf4j
@Service
public class ChatService {

    public static final int MAX_QUESTION_LENGTH = 1000;
    private static final int CACHE_DAYS = 7;
    private static final Pattern FOLLOW_UP = Pattern.compile(
            "(?i)^(and|also|now|what about|how about|only|same|instead|but|then|break (it|that|this) down)\\b"
                    + "|\\b(those|them|these|that|it|same|previous|above|instead|again)\\b");

    private final AiProviderChain aiChain;
    private final SchemaMetadataService metadataService;
    private final SqlSafetyValidator validator;
    private final ReportExecutionService executionService;
    private final PredefinedReportService reportService;
    private final ChatHistoryRepository historyRepository;
    private final UserAiContextService contextService;
    private final int maxExamples;

    public ChatService(AiProviderChain aiChain,
                       SchemaMetadataService metadataService,
                       SqlSafetyValidator validator,
                       ReportExecutionService executionService,
                       PredefinedReportService reportService,
                       ChatHistoryRepository historyRepository,
                       UserAiContextService contextService,
                       @Value("${app.ai.max-examples:4}") int maxExamples) {
        this.aiChain = aiChain;
        this.metadataService = metadataService;
        this.validator = validator;
        this.executionService = executionService;
        this.reportService = reportService;
        this.historyRepository = historyRepository;
        this.contextService = contextService;
        this.maxExamples = maxExamples;
    }

    public ChatResult ask(UserScope scope, String rawQuestion, ReportFilters filters, Integer pageSize) {
        long start = System.currentTimeMillis();
        String question = rawQuestion == null ? "" : rawQuestion.trim();
        if (question.isEmpty()) {
            throw new ReportAccessException(HttpStatus.BAD_REQUEST, "EMPTY_QUESTION", "Please enter a question.");
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new ReportAccessException(HttpStatus.BAD_REQUEST, "QUESTION_TOO_LONG",
                    "Questions are limited to " + MAX_QUESTION_LENGTH + " characters.");
        }
        String conversationId = contextService.currentConversationId(scope.userId());
        boolean followUp = isFollowUp(question);
        ReportingCatalog catalog = metadataService.getCatalog();

        // 1. Reuse SQL from an identical earlier question (no AI call, saves tokens)
        if (!followUp) {
            Optional<ChatRecord> cached = historyRepository.findReusable(scope.userId(), normalize(question), CACHE_DAYS);
            if (cached.isPresent()) {
                try {
                    SecureQuery q = validator.validate(cached.get().sql(), scope, catalog, filters);
                    ChatRecord c = cached.get();
                    return execute(scope, conversationId, question, q, "CACHE", "CACHE", c.understanding(),
                            c.title(), c.explanation(), c.chartType(), pageSize, start, List.of());
                } catch (SqlValidationException e) {
                    log.debug("Cached SQL no longer valid for user {}: {}", scope.userId(), e.getMessage());
                }
            }
        }

        // 2. Ask the AI (primary provider, then fallbacks)
        AiReportResponse ai;
        String provider;
        List<String> aiFailures;
        try {
            List<PromptBuilder.PreviousTurn> previous = followUp
                    ? contextService.recentTurns(scope.userId(), conversationId, 2) : List.of();
            String system = PromptBuilder.systemPrompt(catalog, scope, examplesFor(question, scope));
            AiProviderChain.Completion<AiReportResponse> completion =
                    aiChain.complete(system, PromptBuilder.userPrompt(question, previous), AiResponseParser::parse);
            ai = completion.value();
            provider = completion.label();
            aiFailures = completion.failures();
        } catch (AiException e) {
            log.warn("AI request failed for user {}: {}", scope.userId(), e.getMessage());
            return failure(scope, conversationId, question, ChatStatus.AI_ERROR, "AI_ERROR",
                    "The AI service is currently unavailable: " + e.getMessage(), null, null, start,
                    aiChain.primaryLabel(), List.of());
        }

        if (!ai.answerable()) {
            String msg = ai.clarification() != null ? ai.clarification()
                    : "I could not answer that from the approved business data. Try asking about payments, devices, locations or settlements.";
            return failure(scope, conversationId, question, ChatStatus.NOT_ANSWERABLE, "NOT_ANSWERABLE", msg,
                    ai.understanding(), null, start, provider, aiFailures);
        }

        // 3. Validate and scope the SQL in Java (never trust the AI)
        SecureQuery query;
        try {
            query = validator.validate(ai.sql(), scope, catalog, filters);
        } catch (SqlValidationException e) {
            log.info("Rejected AI SQL for user {} [{}]: {}", scope.userId(), e.getCode(), e.getMessage());
            return failure(scope, conversationId, question, ChatStatus.REJECTED, e.getCode(),
                    "The generated query was rejected: " + e.getMessage(), ai.understanding(), ai.sql(), start,
                    provider, aiFailures);
        }
        return execute(scope, conversationId, question, query, "AI", provider,
                ai.understanding(), ai.title(), ai.explanation(), ai.chartType(), pageSize, start, aiFailures);
    }

    /** Configured AI provider chain (primary + fallbacks) for display; never includes API keys. */
    public List<Map<String, Object>> aiProviders() {
        return aiChain.describe();
    }

    /** Runs a predefined report and stores it in the history like a chat answer. */
    public ChatResult runReport(UserScope scope, String reportId, Map<String, String> params,
                                ReportFilters filters, Integer pageSize) {
        long start = System.currentTimeMillis();
        PredefinedReport report = reportService.find(reportId)
                .orElseThrow(() -> new ReportAccessException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "Report not found"));
        String sql = reportService.renderSql(report, params);
        SecureQuery query;
        try {
            query = validator.validate(sql, scope, metadataService.getCatalog(), filters);
        } catch (SqlValidationException e) {
            if ("UNAUTHORIZED_TABLE".equals(e.getCode()) || "RESTRICTED_TABLE".equals(e.getCode())) {
                throw new ReportAccessException(HttpStatus.FORBIDDEN, e.getCode(), "You are not allowed to run this report.");
            }
            throw e;
        }
        String conversationId = contextService.currentConversationId(scope.userId());
        return execute(scope, conversationId, report.name(), query, "PREDEFINED", "PREDEFINED",
                report.description(), report.name(), report.description(), report.chartType(), pageSize, start, List.of());
    }

    /** Predefined reports the user is allowed to run (validated against the user's scope). */
    public List<PredefinedReport> availableReports(UserScope scope) {
        ReportingCatalog catalog = metadataService.getCatalog();
        return reportService.all().stream().filter(r -> {
            try {
                validator.validate(reportService.renderSql(r, Map.of()), scope, catalog, ReportFilters.none());
                return true;
            } catch (SqlValidationException e) {
                return false;
            }
        }).toList();
    }

    /** Another page (and/or filtered view) of a stored answer. Re-validates with the user's current scope. */
    public ReportPage results(UserScope scope, int chatId, int page, Integer pageSize, ReportFilters filters) {
        SecureQuery query = secureQueryFor(scope, chatId, filters);
        return executionService.executePage(query, page, pageSize);
    }

    /** Validated query of a stored successful answer owned by the user (used by results and exports). */
    public SecureQuery secureQueryFor(UserScope scope, int chatId, ReportFilters filters) {
        ChatRecord record = findOwned(scope, chatId);
        if (!record.isSuccess() || record.sql() == null) {
            throw new ReportAccessException(HttpStatus.CONFLICT, "NO_RESULTS", "This question has no report results.");
        }
        return validator.validate(record.sql(), scope, metadataService.getCatalog(), filters);
    }

    /** The user's own chat history (never other users'). */
    public List<ChatRecord> history(UserScope scope, String conversationId, int limit) {
        return historyRepository.listForUser(scope.userId(), conversationId, limit);
    }

    public ChatRecord findOwned(UserScope scope, int chatId) {
        return historyRepository.findForUser(chatId, scope.userId())
                .orElseThrow(() -> new ReportAccessException(HttpStatus.NOT_FOUND, "CHAT_NOT_FOUND", "Conversation item not found"));
    }

    private ChatResult execute(UserScope scope, String conversationId, String question, SecureQuery query,
                               String source, String provider, String understanding, String title,
                               String explanation, String chartType, Integer pageSize, long start,
                               List<String> aiFailures) {
        String chart = AiReportResponse.normalizeChartType(chartType);
        String reportTitle = title == null || title.isBlank() ? "Report" : title;
        ReportPage page;
        try {
            page = executionService.executePage(query, 1, pageSize);
        } catch (ReportExecutionException e) {
            log.warn("Report execution failed for user {}: {}", scope.userId(), e.getMessage());
            return failure(scope, conversationId, question, ChatStatus.EXECUTION_ERROR, "EXECUTION_ERROR",
                    e.getMessage(), understanding, query.originalSql(), start, provider, aiFailures);
        }
        int duration = (int) (System.currentTimeMillis() - start);
        int chatId = historyRepository.insert(new ChatRecord(0, scope.userId(), conversationId, question,
                understanding, query.originalSql(), ChatStatus.SUCCESS.dbValue(), reportTitle, explanation, chart,
                (int) Math.min(Integer.MAX_VALUE, page.totalRows()), null, dbProvider(provider), source, duration, null));
        contextService.recordTurn(scope.userId(), conversationId, chatId, question, reportTitle,
                query.originalSql(), chart, topicOf(query.tables()));
        return new ChatResult(chatId, conversationId, question, ChatStatus.SUCCESS, source, provider, understanding,
                reportTitle, explanation, chart, query.originalSql(), null, null, page, duration, aiFailures);
    }

    private ChatResult failure(UserScope scope, String conversationId, String question, ChatStatus status,
                               String code, String message, String understanding, String sql, long start,
                               String provider, List<String> aiFailures) {
        int duration = (int) (System.currentTimeMillis() - start);
        int chatId = historyRepository.insert(new ChatRecord(0, scope.userId(), conversationId, question,
                understanding, sql, status.dbValue(), null, null, null, null,
                "[" + code + "] " + message, dbProvider(provider), "AI", duration, null));
        return new ChatResult(chatId, conversationId, question, status, "AI", provider, understanding, null, null,
                null, sql, code, message, null, duration, aiFailures);
    }

    private List<PromptBuilder.Example> examplesFor(String question, UserScope scope) {
        return reportService.relevantExamples(question, scope.allowedTables(), maxExamples).stream()
                .map(r -> new PromptBuilder.Example(r.name(), r.description(),
                        r.examples().isEmpty() ? null : r.examples().get(0), reportService.renderSql(r, Map.of())))
                .toList();
    }

    /** app.chat_history.ai_provider is VARCHAR(30): store a compact "provider:model" (vendor prefix dropped). */
    static String dbProvider(String provider) {
        String p = provider;
        int colon = p.indexOf(':');
        int slash = p.lastIndexOf('/');
        if (colon > 0 && slash > colon) {
            p = p.substring(0, colon + 1) + p.substring(slash + 1);
        }
        return p.length() > 30 ? p.substring(0, 30) : p;
    }

    static boolean isFollowUp(String question) {
        return FOLLOW_UP.matcher(question).find();
    }

    static String normalize(String question) {
        return question.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static String topicOf(Set<String> tables) {
        if (tables.contains("txn.settlement")) return "settlement";
        if (tables.contains("txn.payment_transaction")) return "payment";
        if (tables.stream().anyMatch(t -> t.startsWith("device."))) return "device";
        if (tables.contains("corp.customer")) return "customer";
        return "location";
    }
}
