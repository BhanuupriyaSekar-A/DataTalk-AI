package com.datatalk.datatalkai.reporting;

import java.util.Set;

/**
 * Result of a successful SQL validation.
 *
 * @param originalSql   SQL as produced by the AI / predefined report
 * @param executableSql validated SQL wrapped with Java-generated security scope CTEs (no LIMIT)
 * @param tables        approved tables referenced by the query
 */
public record SecureQuery(String originalSql, String executableSql, Set<String> tables) {
}
