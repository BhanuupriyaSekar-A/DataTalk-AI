package com.datatalk.datatalkai.reports;

import com.datatalk.datatalkai.reporting.SqlValidationException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Loads predefined reports and selects the most relevant ones as AI examples.
 *
 * File format (header comment lines followed by SQL):
 * <pre>
 * -- name: Daily sales trend
 * -- description: ...
 * -- param: days | integer | 30 | Number of days to look back
 * -- example: Show daily sales for the last 30 days
 * -- chart: line
 * SELECT ...
 * </pre>
 */
@Slf4j
@Service
public class PredefinedReportService {

    private static final Pattern PARAM_REF = Pattern.compile(":([a-z_]+)\\b");
    private static final Set<String> STOP_WORDS = Set.of("the", "a", "an", "of", "for", "by", "in", "on", "show",
            "me", "what", "is", "are", "and", "to", "with", "how", "many", "much", "last", "this", "which", "from");
    private static final int MAX_PARAM_VALUE = 3650;

    private final Map<String, PredefinedReport> reports = new LinkedHashMap<>();

    @PostConstruct
    public void load() throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:predefined-reports/**/*.sql");
        List<PredefinedReport> loaded = new ArrayList<>();
        for (Resource file : files) {
            String url = file.getURL().toString();
            String category = url.replaceAll(".*/predefined-reports/([^/]+)/[^/]+$", "$1");
            String fileName = Objects.requireNonNull(file.getFilename()).replace(".sql", "");
            String content = new String(file.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            loaded.add(parse(category + "-" + fileName.replaceFirst("^\\d+-", ""), category, content));
        }
        loaded.sort(Comparator.comparing(PredefinedReport::category).thenComparing(PredefinedReport::id));
        reports.clear();
        loaded.forEach(r -> reports.put(r.id(), r));
        log.info("Loaded {} predefined reports", reports.size());
    }

    static PredefinedReport parse(String id, String category, String content) {
        String name = id;
        String description = "";
        String chart = "table";
        List<PredefinedReport.Parameter> params = new ArrayList<>();
        List<String> examples = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        for (String line : content.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("-- ") && t.contains(":") && sql.isEmpty()) {
                String key = t.substring(3, t.indexOf(':')).trim().toLowerCase();
                String value = t.substring(t.indexOf(':') + 1).trim();
                switch (key) {
                    case "name" -> name = value;
                    case "description" -> description = value;
                    case "chart" -> chart = value;
                    case "example" -> examples.add(value);
                    case "param" -> {
                        String[] p = value.split("\\|");
                        params.add(new PredefinedReport.Parameter(p[0].trim(), p[1].trim(),
                                Integer.parseInt(p[2].trim()), p.length > 3 ? p[3].trim() : ""));
                    }
                    default -> { }
                }
            } else if (!t.isEmpty()) {
                sql.append(line).append('\n');
            }
        }
        return new PredefinedReport(id, category, name, description, List.copyOf(params), List.copyOf(examples),
                chart, sql.toString().trim());
    }

    public List<PredefinedReport> all() {
        return List.copyOf(reports.values());
    }

    public Optional<PredefinedReport> find(String id) {
        return Optional.ofNullable(reports.get(id));
    }

    /**
     * Builds runnable SQL by substituting validated integer parameters (defaults used when missing).
     */
    public String renderSql(PredefinedReport report, Map<String, String> values) {
        Map<String, Integer> resolved = new HashMap<>();
        for (PredefinedReport.Parameter p : report.parameters()) {
            String raw = values == null ? null : values.get(p.name());
            int v = p.defaultValue();
            if (raw != null && !raw.isBlank()) {
                try {
                    v = Integer.parseInt(raw.trim());
                } catch (NumberFormatException e) {
                    throw new SqlValidationException("INVALID_PARAMETER", "Parameter '" + p.name() + "' must be a whole number.");
                }
            }
            if (v < 1 || v > MAX_PARAM_VALUE) {
                throw new SqlValidationException("INVALID_PARAMETER",
                        "Parameter '" + p.name() + "' must be between 1 and " + MAX_PARAM_VALUE + ".");
            }
            resolved.put(p.name(), v);
        }
        Matcher m = PARAM_REF.matcher(report.sqlTemplate());
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Integer v = resolved.get(m.group(1));
            // "::date" casts are not parameters
            boolean isCast = m.start() > 0 && report.sqlTemplate().charAt(m.start() - 1) == ':';
            m.appendReplacement(out, v == null || isCast ? Matcher.quoteReplacement(m.group()) : String.valueOf(v));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Returns up to {@code limit} reports most similar to the question (simple keyword overlap). */
    public List<PredefinedReport> relevantExamples(String question, Set<String> allowedTables, int limit) {
        Set<String> words = tokens(question);
        return reports.values().stream()
                .filter(r -> usesOnly(r, allowedTables))
                .map(r -> Map.entry(r, score(r, words)))
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<PredefinedReport, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private static boolean usesOnly(PredefinedReport r, Set<String> allowedTables) {
        Matcher m = Pattern.compile("\\b(corp|location|device|txn)\\.([a-z_]+)").matcher(r.sqlTemplate());
        while (m.find()) {
            if (!allowedTables.contains(m.group(1) + "." + m.group(2))) {
                return false;
            }
        }
        return true;
    }

    private static int score(PredefinedReport r, Set<String> words) {
        Set<String> reportWords = tokens(r.name() + " " + r.description() + " " + String.join(" ", r.examples()));
        int score = 0;
        for (String w : words) {
            if (reportWords.contains(w)) {
                score++;
            }
        }
        return score;
    }

    static Set<String> tokens(String text) {
        Set<String> out = new HashSet<>();
        for (String w : text.toLowerCase().split("[^a-z0-9]+")) {
            if (w.length() > 2 && !STOP_WORDS.contains(w)) {
                out.add(w.endsWith("s") && w.length() > 4 ? w.substring(0, w.length() - 1) : w);
            }
        }
        return out;
    }
}
