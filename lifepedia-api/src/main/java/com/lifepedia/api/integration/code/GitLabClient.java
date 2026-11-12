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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** API v4 do GitLab (gitlab.com ou servidor próprio aberto na internet) com token pessoal. */
@Component
public class GitLabClient implements CodeHost {

    /** MRs da pessoa com detalhes (pipeline, aprovação, issues) por sincronização. */
    private static final int MAX_DETAILED = 30;
    private static final int EVENT_PAGES = 5;

    private final ApiCalls api;

    public GitLabClient(ApiCalls api) {
        this.api = api;
    }

    @Override
    public IntegrationProvider provider() {
        return IntegrationProvider.GITLAB;
    }

    @Override
    public Identity identify(String baseUrl, String token) throws IntegrationException {
        JsonNode user = get(baseUrl, token, "/user").body();
        String id = Json.text(user, "id");
        String login = Json.text(user, "username");
        if (id == null || login == null) throw IntegrationException.of(Kind.INVALID);
        LocalDate expires = null;
        boolean write = true;
        try {
            JsonNode self = get(baseUrl, token, "/personal_access_tokens/self").body();
            expires = Json.date(Json.text(self, "expires_at"));
            List<String> scopes = Json.strings(self.path("scopes"));
            if (!scopes.contains("api") && !scopes.contains("read_api")) {
                throw new IntegrationException(Kind.FORBIDDEN, "O token precisa da permissão read_api (ou api, para mover as issues).");
            }
            write = scopes.contains("api");
        } catch (IntegrationException e) {
            if (e.kind() == Kind.FORBIDDEN || e.kind() == Kind.AUTH) throw e;
        }
        return new Identity(id, login, expires, write);
    }

    @Override
    public List<RemoteRepo> repos(Connection connection, String query) throws IntegrationException {
        String search = query == null || query.isBlank() ? "" : "&search=" + Json.encode(query.trim());
        JsonNode list = get(connection, "/projects?membership=true&simple=true&per_page=50&order_by=last_activity_at"
                + "&search_namespaces=true" + search).body();
        List<RemoteRepo> repos = new ArrayList<>();
        for (JsonNode project : list) repos.add(repo(project));
        return repos;
    }

    @Override
    public RemoteRepo repo(Connection connection, String externalId) throws IntegrationException {
        return repo(get(connection, "/projects/" + projectId(externalId)).body());
    }

    @Override
    public List<String> labels(Connection connection, String repoId) throws IntegrationException {
        return Json.strings(get(connection, "/projects/" + projectId(repoId) + "/labels?per_page=100&include_ancestor_groups=true").body());
    }

    @Override
    public Snapshot snapshot(Connection connection, Collection<RemoteRepo> repos, OffsetDateTime mergedSince)
            throws IntegrationException {
        Set<String> ids = repos.stream().map(RemoteRepo::externalId).collect(Collectors.toSet());
        Map<String, RemoteMr> byId = new LinkedHashMap<>();
        int detailed = 0;
        String since = mergedSince.withOffsetSameInstant(ZoneOffset.UTC).toString();
        for (JsonNode mr : get(connection, "/merge_requests?scope=created_by_me&state=opened&per_page=100").body()) {
            if (ids.contains(Json.text(mr, "project_id"))) {
                byId.put(Json.text(mr, "id"), mergeRequest(connection, mr, true, false, detailed++));
            }
        }
        for (JsonNode mr : get(connection, "/merge_requests?scope=created_by_me&state=merged&per_page=100&updated_after="
                + Json.encode(since)).body()) {
            if (ids.contains(Json.text(mr, "project_id"))) {
                byId.put(Json.text(mr, "id"), mergeRequest(connection, mr, true, false, detailed++));
            }
        }
        for (JsonNode mr : get(connection, "/merge_requests?scope=all&state=opened&per_page=100&reviewer_username="
                + Json.encode(connection.login())).body()) {
            String id = Json.text(mr, "id");
            if (ids.contains(Json.text(mr, "project_id")) && !byId.containsKey(id)) {
                byId.put(id, mergeRequest(connection, mr, false, true, MAX_DETAILED));
            }
        }
        List<RemoteIssue> issues = new ArrayList<>();
        for (JsonNode issue : get(connection, "/issues?scope=assigned_to_me&state=opened&per_page=100").body()) {
            String repoId = Json.text(issue, "project_id");
            if (!ids.contains(repoId)) continue;
            issues.add(new RemoteIssue(repoId, Json.text(issue, "iid"), Json.text(issue, "title", 300), Json.text(issue, "web_url"),
                    Json.text(issue.path("author"), "username"), Json.strings(issue.path("labels")),
                    Json.date(Json.text(issue, "due_date")), Json.time(issue, "updated_at")));
        }
        List<RemotePipeline> pipelines = new ArrayList<>();
        for (RemoteRepo repo : repos) {
            if (repo.defaultBranch() == null) continue;
            for (JsonNode pipeline : get(connection, "/projects/" + projectId(repo.externalId()) + "/pipelines?per_page=1&ref="
                    + Json.encode(repo.defaultBranch())).body()) {
                pipelines.add(new RemotePipeline(repo.externalId(), Json.text(pipeline, "id"),
                        Pipelines.normalize(Json.text(pipeline, "status"), null), Json.text(pipeline, "ref"),
                        Json.text(pipeline, "web_url"), Json.time(pipeline, "updated_at")));
            }
        }
        return new Snapshot(List.copyOf(byId.values()), issues, pipelines);
    }

    @Override
    public boolean issueOpen(Connection connection, IssueRef issue) throws IntegrationException {
        return "opened".equals(Json.text(get(connection, issuePath(issue)).body(), "state"));
    }

    @Override
    public void relabel(Connection connection, IssueRef issue, List<String> add, List<String> remove) throws IntegrationException {
        StringBuilder query = new StringBuilder();
        if (!add.isEmpty()) query.append("add_labels=").append(Json.encode(String.join(",", add)));
        if (!remove.isEmpty()) {
            query.append(query.isEmpty() ? "" : "&").append("remove_labels=").append(Json.encode(String.join(",", remove)));
        }
        if (query.isEmpty()) return;
        api.call("PUT", url(connection.baseUrl(), issuePath(issue) + "?" + query), headers(connection.token()), null, null);
    }

    @Override
    public void closeIssue(Connection connection, IssueRef issue) throws IntegrationException {
        api.call("PUT", url(connection.baseUrl(), issuePath(issue) + "?state_event=close"), headers(connection.token()), null, null);
    }

    @Override
    public List<RemoteActivity> activity(Connection connection, Collection<String> repoIds, LocalDate since, ZoneId zone)
            throws IntegrationException {
        Map<String, int[]> counts = new HashMap<>();
        for (int page = 1; page <= EVENT_PAGES; page++) {
            JsonNode events = get(connection, "/users/" + connection.externalId() + "/events?per_page=100&page=" + page
                    + "&after=" + since.minusDays(1)).body();
            for (JsonNode event : events) {
                String repoId = Json.text(event, "project_id");
                OffsetDateTime at = Json.time(event, "created_at");
                if (repoId == null || at == null || !repoIds.contains(repoId)) continue;
                LocalDate day = at.atZoneSameInstant(zone).toLocalDate();
                if (day.isBefore(since)) continue;
                int[] count = counts.computeIfAbsent(repoId + "|" + day, k -> new int[4]);
                String action = Json.text(event, "action_name");
                String target = Json.text(event, "target_type");
                if (action == null) continue;
                if (action.startsWith("pushed")) count[0] += Math.max(1, event.path("push_data").path("commit_count").asInt(1));
                else if ("MergeRequest".equals(target) && action.equals("opened")) count[1]++;
                else if ("MergeRequest".equals(target) && action.equals("accepted")) count[2]++;
                else if (action.equals("approved") || action.equals("commented on")
                        && "MergeRequest".equals(Json.text(event.path("note"), "noteable_type"))) count[3]++;
            }
            if (events.size() < 100) break;
        }
        List<RemoteActivity> activity = new ArrayList<>();
        counts.forEach((key, c) -> {
            int bar = key.indexOf('|');
            activity.add(new RemoteActivity(key.substring(0, bar), LocalDate.parse(key.substring(bar + 1)), c[0], c[1], c[2], c[3]));
        });
        return activity;
    }

    private RemoteMr mergeRequest(Connection connection, JsonNode mr, boolean mine, boolean review, int index)
            throws IntegrationException {
        String repoId = Json.text(mr, "project_id");
        String iid = Json.text(mr, "iid");
        String branch = Json.text(mr, "source_branch", 200);
        String pipeline = null;
        boolean approved = false;
        List<IssueRef> issues = List.of();
        if (mine && index < MAX_DETAILED) {
            String base = "/projects/" + repoId + "/merge_requests/" + iid;
            JsonNode detail = get(connection, base).body();
            JsonNode head = detail.path("head_pipeline");
            if (head.isObject()) pipeline = Pipelines.normalize(Json.text(head, "status"), null);
            JsonNode approvals = get(connection, base + "/approvals").body();
            approved = approvals.path("approved").asBoolean(false) && approvals.path("approved_by").size() > 0;
            Set<IssueRef> linked = new LinkedHashSet<>();
            for (JsonNode issue : get(connection, base + "/closes_issues").body()) {
                String project = Json.text(issue, "project_id");
                String number = Json.text(issue, "iid");
                if (project != null && number != null) linked.add(new IssueRef(project, number));
            }
            linked.addAll(Pipelines.linkedIssues(repoId, null, branch));
            issues = List.copyOf(linked);
        }
        boolean draft = mr.path("draft").asBoolean(false) || mr.path("work_in_progress").asBoolean(false);
        return new RemoteMr(repoId, Json.text(mr, "id"), iid, Json.text(mr, "title", 300), Json.text(mr, "state"),
                Json.text(mr, "web_url"), Json.text(mr.path("author"), "username"), mine, review, draft, branch, pipeline,
                approved, mr.path("user_notes_count").asInt(0), issues, Json.time(mr, "created_at"), Json.time(mr, "updated_at"),
                Json.time(mr, "merged_at"));
    }

    private static RemoteRepo repo(JsonNode project) throws IntegrationException {
        String id = Json.text(project, "id");
        String name = Json.text(project, "path_with_namespace", 300);
        if (id == null || name == null) throw IntegrationException.of(Kind.INVALID);
        return new RemoteRepo(id, name, Json.text(project, "web_url", 500), Json.text(project, "default_branch", 200));
    }

    private static String issuePath(IssueRef issue) throws IntegrationException {
        if (!issue.number().matches("\\d{1,9}")) throw IntegrationException.of(Kind.NOT_FOUND);
        return "/projects/" + projectId(issue.repoId()) + "/issues/" + issue.number();
    }

    private static String projectId(String id) throws IntegrationException {
        if (id == null || !id.matches("\\d{1,12}")) throw IntegrationException.of(Kind.NOT_FOUND);
        return id;
    }

    private ApiCalls.Result get(Connection connection, String path) throws IntegrationException {
        return get(connection.baseUrl(), connection.token(), path);
    }

    private ApiCalls.Result get(String baseUrl, String token, String path) throws IntegrationException {
        return api.get(url(baseUrl, path), headers(token));
    }

    private static URI url(String baseUrl, String path) {
        return URI.create(baseUrl + "/api/v4" + path);
    }

    private static Map<String, String> headers(String token) {
        return Map.of("PRIVATE-TOKEN", token);
    }
}
