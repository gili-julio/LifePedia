package com.lifepedia.api.integration.code;

import com.lifepedia.api.integration.ApiCalls;
import com.lifepedia.api.integration.Connection;
import com.lifepedia.api.integration.IntegrationException;
import com.lifepedia.api.integration.IntegrationException.Kind;
import com.lifepedia.api.integration.IntegrationProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** API REST do GitHub com token pessoal (clássico ou "fine-grained"). */
@Component
public class GitHubClient implements CodeHost {

    private static final String API = "https://api.github.com";
    private static final String REPOS = "/repos/";
    private static final int MAX_DETAILED = 30;
    private static final int EVENT_PAGES = 3;

    private final ApiCalls api;

    public GitHubClient(ApiCalls api) {
        this.api = api;
    }

    @Override
    public IntegrationProvider provider() {
        return IntegrationProvider.GITHUB;
    }

    @Override
    public Identity identify(String baseUrl, String token) throws IntegrationException {
        ApiCalls.Result result = api.get(url("/user"), headers(token));
        String id = Json.text(result.body(), "id");
        String login = Json.text(result.body(), "login");
        if (id == null || login == null) throw IntegrationException.of(Kind.INVALID);
        String scopes = result.header("x-oauth-scopes");
        boolean write = scopes == null || scopes.contains("repo");
        return new Identity(id, login, Json.date(result.header("github-authentication-token-expiration")), write);
    }

    @Override
    public List<RemoteRepo> repos(Connection connection, String query) throws IntegrationException {
        String filter = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<RemoteRepo> repos = new ArrayList<>();
        for (JsonNode repo : get(connection, "/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator,organization_member")) {
            RemoteRepo remote = repo(repo);
            if (remote.fullName().toLowerCase(Locale.ROOT).contains(filter) && repos.size() < 50) repos.add(remote);
        }
        return repos;
    }

    @Override
    public RemoteRepo repo(Connection connection, String externalId) throws IntegrationException {
        return repo(get(connection, REPOS + repoPath(externalId)));
    }

    @Override
    public List<String> labels(Connection connection, String repoId) throws IntegrationException {
        return Json.strings(get(connection, REPOS + repoPath(repoId) + "/labels?per_page=100"));
    }

    @Override
    public Snapshot snapshot(Connection connection, Collection<RemoteRepo> repos, OffsetDateTime mergedSince)
            throws IntegrationException {
        Set<String> ids = repos.stream().map(r -> r.externalId().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Map<String, RemoteMr> byId = new LinkedHashMap<>();
        int detailed = 0;
        for (JsonNode pr : search(connection, "is:pr is:open author:@me")) {
            String repoId = repoOf(pr);
            if (ids.contains(repoId.toLowerCase(Locale.ROOT))) {
                byId.put(Json.text(pr, "id"), pull(connection, repoId, pr, true, false, detailed++));
            }
        }
        for (JsonNode pr : search(connection, "is:pr is:merged author:@me merged:>=" + mergedSince.toLocalDate())) {
            String repoId = repoOf(pr);
            if (ids.contains(repoId.toLowerCase(Locale.ROOT))) {
                byId.put(Json.text(pr, "id"), pull(connection, repoId, pr, true, false, detailed++));
            }
        }
        for (JsonNode pr : search(connection, "is:pr is:open review-requested:@me")) {
            String repoId = repoOf(pr);
            String id = Json.text(pr, "id");
            if (ids.contains(repoId.toLowerCase(Locale.ROOT)) && !byId.containsKey(id)) {
                byId.put(id, pull(connection, repoId, pr, false, true, MAX_DETAILED));
            }
        }
        List<RemoteIssue> issues = new ArrayList<>();
        for (JsonNode issue : search(connection, "is:issue is:open assignee:@me")) {
            String repoId = repoOf(issue);
            if (!ids.contains(repoId.toLowerCase(Locale.ROOT))) continue;
            issues.add(new RemoteIssue(repoId, Json.text(issue, "number"), Json.text(issue, "title", 300), Json.text(issue, "html_url"),
                    Json.text(issue.path("user"), "login"), Json.strings(issue.path("labels")),
                    Json.date(Json.text(issue.path("milestone"), "due_on")), Json.time(issue, "updated_at")));
        }
        List<RemotePipeline> pipelines = new ArrayList<>();
        for (RemoteRepo repo : repos) {
            if (repo.defaultBranch() == null) continue;
            for (JsonNode run : get(connection, REPOS + repoPath(repo.externalId()) + "/actions/runs?per_page=1&branch="
                    + Json.encode(repo.defaultBranch())).path("workflow_runs")) {
                pipelines.add(new RemotePipeline(repo.externalId(), Json.text(run, "id"),
                        Pipelines.normalize(Json.text(run, "status"), Json.text(run, "conclusion")), Json.text(run, "head_branch"),
                        Json.text(run, "html_url"), Json.time(run, "updated_at")));
            }
        }
        return new Snapshot(List.copyOf(byId.values()), issues, pipelines);
    }

    @Override
    public boolean issueOpen(Connection connection, IssueRef issue) throws IntegrationException {
        return "open".equals(Json.text(get(connection, issuePath(issue)), "state"));
    }

    @Override
    public void relabel(Connection connection, IssueRef issue, List<String> add, List<String> remove) throws IntegrationException {
        String path = issuePath(issue) + "/labels";
        if (!add.isEmpty()) api.call("POST", url(path), headers(connection.token()), Map.of("labels", add), null);
        for (String label : remove) {
            try {
                api.call("DELETE", url(path + "/" + Json.encode(label)), headers(connection.token()), null, null);
            } catch (IntegrationException e) {
                if (e.kind() != Kind.NOT_FOUND) throw e;
            }
        }
    }

    @Override
    public void closeIssue(Connection connection, IssueRef issue) throws IntegrationException {
        api.call("PATCH", url(issuePath(issue)), headers(connection.token()), Map.of("state", "closed"), null);
    }

    @Override
    public List<RemoteActivity> activity(Connection connection, Collection<String> repoIds, LocalDate since, ZoneId zone)
            throws IntegrationException {
        Set<String> ids = repoIds.stream().map(r -> r.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Map<String, String> original = repoIds.stream().collect(Collectors.toMap(r -> r.toLowerCase(Locale.ROOT), r -> r, (a, b) -> a));
        Map<String, int[]> counts = new HashMap<>();
        for (int page = 1; page <= EVENT_PAGES; page++) {
            JsonNode events = get(connection, "/users/" + Json.encode(connection.login()) + "/events?per_page=100&page=" + page);
            boolean older = false;
            for (JsonNode event : events) {
                String repo = Json.text(event.path("repo"), "name");
                OffsetDateTime at = Json.time(event, "created_at");
                if (repo == null || at == null) continue;
                LocalDate day = at.atZoneSameInstant(zone).toLocalDate();
                if (day.isBefore(since)) {
                    older = true;
                    continue;
                }
                if (!ids.contains(repo.toLowerCase(Locale.ROOT))) continue;
                int[] count = counts.computeIfAbsent(original.get(repo.toLowerCase(Locale.ROOT)) + "|" + day, k -> new int[4]);
                JsonNode payload = event.path("payload");
                String action = Json.text(payload, "action");
                switch (Json.text(event, "type")) {
                    case "PushEvent" -> count[0] += Math.max(1, payload.path("distinct_size").asInt(payload.path("size").asInt(1)));
                    case "PullRequestEvent" -> {
                        if ("opened".equals(action)) count[1]++;
                        if ("closed".equals(action) && payload.path("pull_request").path("merged").asBoolean(false)) count[2]++;
                    }
                    case "PullRequestReviewEvent" -> count[3]++;
                    case null, default -> {
                    }
                }
            }
            if (older || events.size() < 100) break;
        }
        List<RemoteActivity> activity = new ArrayList<>();
        counts.forEach((key, c) -> {
            int bar = key.lastIndexOf('|');
            activity.add(new RemoteActivity(key.substring(0, bar), LocalDate.parse(key.substring(bar + 1)), c[0], c[1], c[2], c[3]));
        });
        return activity;
    }

    private RemoteMr pull(Connection connection, String repoId, JsonNode pr, boolean mine, boolean review, int index)
            throws IntegrationException {
        String number = Json.text(pr, "number");
        boolean merged = pr.path("pull_request").path("merged_at").isString();
        String state = merged ? "merged" : "open".equals(Json.text(pr, "state")) ? "opened" : "closed";
        String branch = null;
        String pipeline = null;
        boolean approved = false;
        List<IssueRef> issues = List.of();
        OffsetDateTime mergedAt = Json.time(pr.path("pull_request"), "merged_at");
        if (mine && index < MAX_DETAILED) {
            String base = REPOS + repoPath(repoId) + "/pulls/" + number;
            JsonNode detail = get(connection, base);
            branch = Json.text(detail.path("head"), "ref", 200);
            String sha = Json.text(detail.path("head"), "sha");
            if (sha != null && sha.matches("[0-9a-f]{7,64}")) {
                List<String> runs = new ArrayList<>();
                JsonNode checks = get(connection, REPOS + repoPath(repoId) + "/commits/" + sha + "/check-runs?per_page=50");
                for (JsonNode run : checks.path("check_runs")) {
                    String status = Pipelines.normalize(Json.text(run, "status"), Json.text(run, "conclusion"));
                    if (status != null) runs.add(status);
                }
                pipeline = Pipelines.worst(runs);
            }
            for (JsonNode reviewNode : get(connection, base + "/reviews?per_page=100")) {
                if ("APPROVED".equals(Json.text(reviewNode, "state"))) approved = true;
            }
            issues = Pipelines.linkedIssues(repoId, Json.text(detail, "body"), branch);
            if (mergedAt == null) mergedAt = Json.time(detail, "merged_at");
        }
        return new RemoteMr(repoId, Json.text(pr, "id"), number, Json.text(pr, "title", 300), state, Json.text(pr, "html_url"),
                Json.text(pr.path("user"), "login"), mine, review, pr.path("draft").asBoolean(false), branch, pipeline, approved,
                pr.path("comments").asInt(0), issues, Json.time(pr, "created_at"), Json.time(pr, "updated_at"), mergedAt);
    }

    private JsonNode search(Connection connection, String query) throws IntegrationException {
        return get(connection, "/search/issues?per_page=100&q=" + Json.encode(query)).path("items");
    }

    /** "dono/nome" a partir do {@code repository_url} do resultado da busca. */
    private static String repoOf(JsonNode item) {
        String url = Json.text(item, "repository_url");
        if (url == null || !url.contains(REPOS)) return "";
        return url.substring(url.indexOf(REPOS) + REPOS.length());
    }

    private static RemoteRepo repo(JsonNode repo) throws IntegrationException {
        String name = Json.text(repo, "full_name", 300);
        if (name == null) throw IntegrationException.of(Kind.INVALID);
        return new RemoteRepo(name, name, Json.text(repo, "html_url", 500), Json.text(repo, "default_branch", 200));
    }

    private static String issuePath(IssueRef issue) throws IntegrationException {
        if (!issue.number().matches("\\d{1,9}")) throw IntegrationException.of(Kind.NOT_FOUND);
        return REPOS + repoPath(issue.repoId()) + "/issues/" + issue.number();
    }

    private static String repoPath(String fullName) throws IntegrationException {
        if (fullName == null || !fullName.matches("[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}")) {
            throw IntegrationException.of(Kind.NOT_FOUND);
        }
        return fullName;
    }

    private JsonNode get(Connection connection, String path) throws IntegrationException {
        return api.get(url(path), headers(connection.token())).body();
    }

    private static URI url(String path) {
        return URI.create(API + path);
    }

    private static Map<String, String> headers(String token) {
        return Map.of("Authorization", "Bearer " + token, "Accept", "application/vnd.github+json", "X-GitHub-Api-Version", "2022-11-28");
    }
}
