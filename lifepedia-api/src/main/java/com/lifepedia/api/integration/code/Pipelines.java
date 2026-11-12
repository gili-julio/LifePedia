package com.lifepedia.api.integration.code;

import com.lifepedia.api.integration.code.CodeHost.IssueRef;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Regras comuns a GitLab e GitHub: status de pipeline e issues que um MR fecha. */
public final class Pipelines {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String RUNNING = "RUNNING";
    public static final String PENDING = "PENDING";
    public static final String CANCELED = "CANCELED";
    public static final String SKIPPED = "SKIPPED";
    public static final String MANUAL = "MANUAL";

    private static final Pattern CLOSES = Pattern.compile("(?i)\\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?|implement(?:s|ed)?)"
            + ":?\\s+#(\\d{1,9})\\b");
    private static final Pattern BRANCH = Pattern.compile("^(?:[A-Za-z]+/)?(\\d{1,9})(?:[-_].*)?$");

    private Pipelines() {
    }

    /** Status do GitLab ou do GitHub (status + conclusão) no nome comum. */
    public static String normalize(String status, String conclusion) {
        String value = (status == null ? "" : status).toLowerCase(Locale.ROOT);
        if (value.equals("completed")) value = conclusion == null ? "" : conclusion.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "success", "neutral" -> SUCCESS;
            case "failed", "failure", "timed_out", "startup_failure" -> FAILED;
            case "running", "in_progress" -> RUNNING;
            case "created", "waiting_for_resource", "preparing", "pending", "queued", "requested", "waiting", "scheduled" -> PENDING;
            case "canceled", "cancelled", "stale" -> CANCELED;
            case "skipped" -> SKIPPED;
            case "manual", "action_required" -> MANUAL;
            default -> value.isEmpty() ? null : PENDING;
        };
    }

    /** Pior status entre vários (falhou > rodando > esperando > ok). */
    public static String worst(List<String> statuses) {
        for (String candidate : List.of(FAILED, RUNNING, PENDING, MANUAL, CANCELED, SUCCESS, SKIPPED)) {
            if (statuses.contains(candidate)) return candidate;
        }
        return null;
    }

    /** "Closes #12" na descrição e "12-titulo" (ou "feature/12-...") no branch, no próprio repositório. */
    public static List<IssueRef> linkedIssues(String repoId, String description, String branch) {
        Set<String> numbers = new LinkedHashSet<>();
        if (description != null) {
            Matcher matcher = CLOSES.matcher(description);
            while (matcher.find()) numbers.add(matcher.group(1));
        }
        if (branch != null) {
            Matcher matcher = BRANCH.matcher(branch);
            if (matcher.matches()) numbers.add(matcher.group(1));
        }
        List<IssueRef> refs = new ArrayList<>();
        numbers.forEach(n -> refs.add(new IssueRef(repoId, n)));
        return refs;
    }
}
