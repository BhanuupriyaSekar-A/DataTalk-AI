package com.datatalk.datatalkai.chat;

import com.datatalk.datatalkai.ai.PromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores a compact per-user AI context in app.user_ai_context (JSONB):
 * <pre>
 * { "conversationId": "...",
 *   "preferences": { "lastChartType": "bar", "lastFilters": {...} },
 *   "recentTurns": [ { "chatId": 1, "question": "...", "title": "...", "sql": "..." } ],
 *   "topics": { "payment": 3, "device": 1 },
 *   "updatedAt": "..." }
 * </pre>
 * Only the last few turns are kept so follow-up prompts stay small. Values are sanitized:
 * no passwords, API keys, card numbers or report data are ever stored.
 */
@Slf4j
@Service
public class UserAiContextService {

    static final int MAX_TURNS = 3;
    static final int MAX_SQL_CHARS = 1500;
    static final int MAX_TEXT_CHARS = 300;

    private static final Pattern LONG_NUMBER = Pattern.compile("\\b\\d{12,19}\\b");
    private static final Pattern SECRET_TOKEN = Pattern.compile(
            "(?i)\\b(sk-[a-z0-9_-]{10,}|nvapi-[a-z0-9_-]{10,}|AIza[0-9a-z_-]{20,}|npg_[a-z0-9]{8,})\\b");
    private static final Pattern PASSWORD_PHRASE = Pattern.compile("(?i)(password|passwd|pwd)\\s*[:=]?\\s*\\S+");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public UserAiContextService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public ObjectNode get(int userId) {
        List<String> rows = jdbc.queryForList("SELECT context::text FROM app.user_ai_context WHERE user_id = ?",
                String.class, userId);
        if (!rows.isEmpty()) {
            try {
                JsonNode node = mapper.readTree(rows.get(0));
                if (node instanceof ObjectNode obj) {
                    return obj;
                }
            } catch (Exception e) {
                log.warn("Ignoring unreadable AI context for user {}", userId);
            }
        }
        return mapper.createObjectNode();
    }

    /** Returns the active conversation id, creating one if needed. */
    public String currentConversationId(int userId) {
        ObjectNode ctx = get(userId);
        String id = ctx.path("conversationId").asText(null);
        if (id == null || id.isBlank()) {
            id = startNewConversation(userId);
        }
        return id;
    }

    /** Starts a new conversation: keeps preferences and topics, clears recent turns. */
    public String startNewConversation(int userId) {
        ObjectNode ctx = get(userId);
        String id = UUID.randomUUID().toString();
        ctx.put("conversationId", id);
        ctx.putArray("recentTurns");
        save(userId, ctx);
        return id;
    }

    /** Previous turns of the active conversation, for follow-up questions. */
    public List<PromptBuilder.PreviousTurn> recentTurns(int userId, String conversationId, int max) {
        ObjectNode ctx = get(userId);
        List<PromptBuilder.PreviousTurn> turns = new ArrayList<>();
        if (!conversationId.equals(ctx.path("conversationId").asText())) {
            return turns;
        }
        JsonNode arr = ctx.path("recentTurns");
        for (int i = Math.max(0, arr.size() - max); i < arr.size(); i++) {
            JsonNode t = arr.get(i);
            turns.add(new PromptBuilder.PreviousTurn(t.path("question").asText(), t.path("title").asText(null),
                    t.path("sql").asText(null)));
        }
        return turns;
    }

    /** Records a successful turn and simple preferences. */
    public void recordTurn(int userId, String conversationId, int chatId, String question, String title,
                           String sql, String chartType, String topic) {
        ObjectNode ctx = get(userId);
        if (!conversationId.equals(ctx.path("conversationId").asText())) {
            ctx.put("conversationId", conversationId);
            ctx.putArray("recentTurns");
        }
        ArrayNode turns = ctx.has("recentTurns") && ctx.get("recentTurns").isArray()
                ? (ArrayNode) ctx.get("recentTurns") : ctx.putArray("recentTurns");
        ObjectNode turn = mapper.createObjectNode();
        turn.put("chatId", chatId);
        turn.put("question", sanitize(question, MAX_TEXT_CHARS));
        if (title != null) {
            turn.put("title", sanitize(title, MAX_TEXT_CHARS));
        }
        if (sql != null) {
            turn.put("sql", sanitize(sql, MAX_SQL_CHARS));
        }
        turns.add(turn);
        while (turns.size() > MAX_TURNS) {
            turns.remove(0);
        }
        ObjectNode prefs = ctx.has("preferences") && ctx.get("preferences").isObject()
                ? (ObjectNode) ctx.get("preferences") : ctx.putObject("preferences");
        if (chartType != null) {
            prefs.put("lastChartType", chartType);
        }
        if (topic != null) {
            ObjectNode topics = ctx.has("topics") && ctx.get("topics").isObject()
                    ? (ObjectNode) ctx.get("topics") : ctx.putObject("topics");
            topics.put(topic, topics.path(topic).asInt(0) + 1);
        }
        save(userId, ctx);
    }

    public void saveFilters(int userId, String dateFrom, String dateTo, String status) {
        ObjectNode ctx = get(userId);
        ObjectNode prefs = ctx.has("preferences") && ctx.get("preferences").isObject()
                ? (ObjectNode) ctx.get("preferences") : ctx.putObject("preferences");
        ObjectNode f = prefs.putObject("lastFilters");
        if (dateFrom != null) f.put("dateFrom", dateFrom);
        if (dateTo != null) f.put("dateTo", dateTo);
        if (status != null) f.put("status", status);
        save(userId, ctx);
    }

    private void save(int userId, ObjectNode ctx) {
        ctx.put("updatedAt", LocalDateTime.now().withNano(0).toString());
        jdbc.update("INSERT INTO app.user_ai_context (user_id, context, updated_at) VALUES (?, ?::jsonb, NOW()) "
                + "ON CONFLICT (user_id) DO UPDATE SET context = EXCLUDED.context, updated_at = NOW()",
                userId, ctx.toString());
    }

    /** Masks card-like numbers, API keys and passwords and truncates long text. */
    static String sanitize(String text, int maxChars) {
        if (text == null) {
            return null;
        }
        String s = LONG_NUMBER.matcher(text).replaceAll("[number]");
        s = SECRET_TOKEN.matcher(s).replaceAll("[secret]");
        s = PASSWORD_PHRASE.matcher(s).replaceAll("$1 [hidden]");
        return s.length() > maxChars ? s.substring(0, maxChars) : s;
    }
}
