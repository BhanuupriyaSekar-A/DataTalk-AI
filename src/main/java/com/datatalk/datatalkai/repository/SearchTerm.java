package com.datatalk.datatalkai.repository;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed search text: ILIKE patterns plus a numeric id when the text is an id ("12", "#12", "txn 12").
 */
public record SearchTerm(String text, String like, String typeLike, int id, boolean isId) {

    public static final int MAX_LENGTH = 100;
    private static final Pattern ID = Pattern.compile(
            "^(?:#|id|txn|transaction|device|customer|location)?\\s*[:#-]?\\s*(\\d{1,9})$", Pattern.CASE_INSENSITIVE);

    public static SearchTerm of(String q) {
        String text = q == null ? "" : q.trim();
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Search text is too long (max " + MAX_LENGTH + " characters)");
        }
        Matcher m = ID.matcher(text);
        boolean isId = m.matches();
        return new SearchTerm(text, likeContains(text),
                likeContains(text.toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_")),
                isId ? Integer.parseInt(m.group(1)) : -1, isId);
    }

    public boolean isEmpty() {
        return text.isEmpty();
    }

    /** "%text%" for ILIKE with LIKE wildcards in the text escaped. */
    static String likeContains(String text) {
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
