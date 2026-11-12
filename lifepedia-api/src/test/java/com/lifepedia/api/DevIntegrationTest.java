package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import com.lifepedia.api.integration.IntegrationException;
import com.lifepedia.api.integration.IntegrationProvider;
import com.lifepedia.api.integration.IntegrationSyncService;
import com.lifepedia.api.integration.code.CodeHost.IssueRef;
import com.lifepedia.api.integration.code.CodeHost.Identity;
import com.lifepedia.api.integration.code.CodeHost.RemoteActivity;
import com.lifepedia.api.integration.code.CodeHost.RemoteIssue;
import com.lifepedia.api.integration.code.CodeHost.RemoteMr;
import com.lifepedia.api.integration.code.CodeHost.RemotePipeline;
import com.lifepedia.api.integration.code.CodeHost.RemoteRepo;
import com.lifepedia.api.integration.code.CodeHost.Snapshot;
import com.lifepedia.api.integration.code.GitHubClient;
import com.lifepedia.api.integration.code.GitLabClient;
import com.lifepedia.api.integration.code.Pipelines;
import com.lifepedia.api.shared.ai.GeminiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Módulo Dev: projetos, repositórios, raias das issues, tarefas, avisos, painel do dia e daily (GitLab e Gemini são mocks). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DevIntegrationTest extends ApiTestSupport {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String OPENED = "Waiting for merge request";
    private static final String MERGED = "Free for tests";

    @MockitoBean
    GitLabClient gitlab;

    @MockitoBean
    GitHubClient github;

    @MockitoBean
    GeminiClient gemini;

    @Autowired
    IntegrationSyncService sync;

    @Autowired
    JdbcClient jdbc;

    private volatile Snapshot current = new Snapshot(List.of(), List.of(), List.of());

    @BeforeEach
    void mocks() throws IntegrationException {
        when(gitlab.provider()).thenReturn(IntegrationProvider.GITLAB);
        when(github.provider()).thenReturn(IntegrationProvider.GITHUB);
        when(gitlab.snapshot(any(), any(), any())).thenAnswer(call -> current);
        when(gitlab.repo(any(), eq("101"))).thenReturn(new RemoteRepo("101", "gertec/app", "https://gitlab.com/gertec/app", "main"));
    }

    @Test
    void projectsValidationAndIsolation() {
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        String created = post(alice, "/api/dev/projects", """
                {"name":"Gertec","color":"#6D28D9","description":"Trabalho","links":[
                  {"label":"Board","url":"https://gitlab.com/gertec/board","kind":"BOARD"},
                  {"label":"Staging","url":"http://staging.gertec.dev:8443","kind":"STAGING"}]}""");
        String id = JsonPath.read(created, "$.id");
        assertThat(JsonPath.<String>read(created, "$.color")).isEqualTo("#6d28d9");
        assertThat(JsonPath.<List<String>>read(created, "$.links[*].label")).containsExactly("Board", "Staging");
        post(alice, "/api/dev/projects", "{\"name\":\"Sebito\",\"color\":\"#000000\"}");
        String sebito = JsonPath.<List<String>>read(body(send("GET", alice, "/api/dev/projects", null)), "$[*].id").get(1);

        assertThat(send("PUT", alice, "/api/dev/projects/order", "{\"ids\":[\"%s\",\"%s\"]}".formatted(sebito, id))).hasStatus(204);
        assertThat(JsonPath.<List<String>>read(body(send("GET", alice, "/api/dev/projects", null)), "$[*].name"))
                .containsExactly("Sebito", "Gertec");

        assertThat(send("POST", alice, "/api/dev/projects", "{\"name\":\"\",\"color\":\"#000000\"}")).hasStatus(400);
        assertThat(send("POST", alice, "/api/dev/projects", "{\"name\":\"X\",\"color\":\"roxo\"}")).hasStatus(400);
        assertThat(send("POST", alice, "/api/dev/projects", """
                {"name":"X","color":"#000000","links":[{"label":"Ruim","url":"javascript:alert(1)","kind":"OTHER"}]}""")).hasStatus(400);
        assertThat(send("POST", alice, "/api/dev/projects", """
                {"name":"X","color":"#000000","calendarAccountId":"%s"}""".formatted(UUID.randomUUID()))).hasStatus(400);

        assertThat(send("GET", bob, "/api/dev/projects/" + id, null)).hasStatus(404);
        assertThat(send("PUT", bob, "/api/dev/projects/" + id, "{\"name\":\"Y\",\"color\":\"#000000\"}")).hasStatus(404);
        assertThat(send("DELETE", bob, "/api/dev/projects/" + id, null)).hasStatus(404);
        assertThat(JsonPath.<List<Object>>read(body(send("GET", bob, "/api/dev/projects", null)), "$[*]")).isEmpty();
        assertThat(mvc.get().uri("/api/dev/today")).hasStatus(401);

        assertThat(send("DELETE", alice, "/api/dev/projects/" + id, null)).hasStatus(204);
        assertThat(send("GET", alice, "/api/dev/projects/" + id, null)).hasStatus(404);
    }

    @Test
    void lanesTasksAndNotifications() throws IntegrationException {
        String alice = registerAndGetBearer();
        String accountId = connectGitLab(alice, "11");
        String project = JsonPath.read(post(alice, "/api/dev/projects", "{\"name\":\"Gertec\",\"color\":\"#6d28d9\"}"), "$.id");

        when(gitlab.repos(any(), isNull()))
                .thenReturn(List.of(new RemoteRepo("101", "gertec/app", "https://gitlab.com/gertec/app", "main")));
        assertThat(JsonPath.<List<String>>read(body(send("GET", alice, "/api/dev/repo-options?accountId=" + accountId, null)),
                "$[*].fullName")).containsExactly("gertec/app");

        OffsetDateTime now = OffsetDateTime.now();
        LocalDate today = LocalDate.now(ZONE);
        RemoteIssue issue = new RemoteIssue("101", "5", "Corrigir login", "https://gitlab.com/gertec/app/-/issues/5", "lider",
                List.of("Doing"), null, now);
        current = new Snapshot(List.of(mr("opened", Pipelines.SUCCESS, 0, now.plusMinutes(1), null)), List.of(issue),
                List.of(new RemotePipeline("101", "1", Pipelines.SUCCESS, "main", "https://gitlab.com/p/1", now)));
        when(gitlab.activity(any(), anyList(), any(), any())).thenReturn(List.of(new RemoteActivity("101", today, 3, 1, 0, 2)));

        String repo = post(alice, "/api/dev/projects/" + project + "/repos",
                "{\"accountId\":\"%s\",\"externalId\":\"101\"}".formatted(accountId));
        String repoId = JsonPath.read(repo, "$.id");
        assertThat(JsonPath.<Boolean>read(repo, "$.canWrite")).isTrue();
        assertThat(send("POST", alice, "/api/dev/projects/" + project + "/repos", "{\"accountId\":\"%s\",\"externalId\":\"101\"}"
                .formatted(accountId))).hasStatus(409);

        // A issue virou tarefa com a etiqueta do projeto.
        UUID userId = UUID.fromString(userId(alice));
        assertThat(jdbc.sql("select title from tasks where user_id = :u").param("u", userId).query(String.class).list())
                .containsExactly("#5 Corrigir login");
        String detail = body(send("GET", alice, "/api/dev/projects/" + project, null));
        assertThat(JsonPath.<List<String>>read(detail, "$.items[*].kind")).containsExactlyInAnyOrder("MR", "ISSUE", "PIPELINE");
        assertThat(JsonPath.<Integer>read(detail, "$.project.counts.myMergeRequests")).isEqualTo(1);

        assertThat(send("PUT", alice, "/api/dev/repos/" + repoId, """
                {"importIssues":true,"closeIssues":true,"labelMrOpened":"%s","labelMrMerged":"%s"}""".formatted(OPENED, OPENED)))
                .hasStatus(400);
        assertThat(send("PUT", alice, "/api/dev/repos/" + repoId, """
                {"importIssues":true,"closeIssues":true,"labelMrOpened":"%s","labelMrMerged":"%s","labelsClear":["Doing"]}"""
                .formatted(OPENED, MERGED))).hasStatusOk().bodyJson().extractingPath("$.labelsClear").asArray().containsExactly("Doing");

        // MR aberto depois da regra: issue vai para a raia "esperando MR"; pipeline falhou e comentários novos avisam.
        current = new Snapshot(List.of(mr("opened", Pipelines.FAILED, 2, now.plusMinutes(1), null)), List.of(issue),
                List.of(new RemotePipeline("101", "1", Pipelines.SUCCESS, "main", "https://gitlab.com/p/1", now)));
        assertThat(sync.sync(UUID.fromString(accountId), true)).isTrue();
        verify(gitlab, times(1)).relabel(any(), eq(new IssueRef("101", "5")), eq(List.of(OPENED)), eq(List.of("Doing", MERGED)));
        assertThat(types(alice)).contains("DEV_PIPELINE_FAILED", "DEV_MR_COMMENTED");

        String todayPanel = body(send("GET", alice, "/api/dev/today", null));
        assertThat(JsonPath.<List<String>>read(todayPanel, "$.myMergeRequests[*].title")).containsExactly("Tela de login");
        assertThat(JsonPath.<List<String>>read(todayPanel, "$.failing[*].kind")).containsExactly("MR");
        assertThat(JsonPath.<List<String>>read(todayPanel, "$.issues[*].title")).containsExactly("Corrigir login");

        // MR aceito: issue vai para "livre para testes", sai da lista e a tarefa conclui sozinha.
        current = new Snapshot(List.of(mr("merged", Pipelines.SUCCESS, 2, now.plusMinutes(1), now.plusMinutes(2))), List.of(),
                List.of(new RemotePipeline("101", "2", Pipelines.SUCCESS, "main", "https://gitlab.com/p/2", now)));
        when(gitlab.issueOpen(any(), eq(new IssueRef("101", "5")))).thenReturn(false);
        sync.sync(UUID.fromString(accountId), true);
        sync.sync(UUID.fromString(accountId), true);
        verify(gitlab, times(1)).relabel(any(), eq(new IssueRef("101", "5")), eq(List.of(MERGED)), eq(List.of("Doing", OPENED)));
        assertThat(types(alice)).contains("DEV_MR_MERGED");
        assertThat(jdbc.sql("select completed_at is not null from tasks where user_id = :u").param("u", userId)
                .query(Boolean.class).single())
                .isTrue();

        String activity = body(send("GET", alice, "/api/dev/activity?weeks=2", null));
        assertThat(JsonPath.<Integer>read(activity, "$.projects[0].commits")).isEqualTo(3);
        assertThat(JsonPath.<List<Integer>>read(activity, "$.days[-1:].commits")).containsExactly(3);

        when(gitlab.labels(any(), eq("101"))).thenReturn(List.of("Doing", MERGED, OPENED));
        assertThat(JsonPath.<List<String>>read(body(send("GET", alice, "/api/dev/repos/" + repoId + "/labels", null)), "$[*]"))
                .containsExactly("Doing", MERGED, OPENED);

        String bob = registerAndGetBearer();
        assertThat(send("PUT", bob, "/api/dev/repos/" + repoId, "{\"importIssues\":true,\"closeIssues\":false}")).hasStatus(404);
        assertThat(send("GET", bob, "/api/dev/repo-options?accountId=" + accountId, null)).hasStatus(404);
        assertThat(send("POST", bob, "/api/dev/projects/" + project + "/repos", "{\"accountId\":\"%s\",\"externalId\":\"101\"}"
                .formatted(accountId))).hasStatus(404);

        String export = body(send("GET", alice, "/api/account/export", null));
        assertThat(JsonPath.<List<String>>read(export, "$.devProjects[*].repos[*]")).containsExactly("gertec/app");
        assertThat(JsonPath.<List<String>>read(export, "$.integrations[*].provider")).containsExactly("GITLAB");
        assertThat(export).doesNotContain("glpat");

        // Desligar a conta leva os repositórios junto.
        assertThat(send("DELETE", alice, "/api/integrations/" + accountId, null)).hasStatus(204);
        assertThat(JsonPath.<List<Object>>read(body(send("GET", alice, "/api/dev/projects/" + project, null)), "$.project.repos"))
                .isEmpty();
    }

    @Test
    void completingTheTaskClosesTheIssue() throws IntegrationException {
        String alice = registerAndGetBearer();
        String accountId = connectGitLab(alice, "12");
        String project = JsonPath.read(post(alice, "/api/dev/projects", "{\"name\":\"Nut\",\"color\":\"#0ea5e9\"}"), "$.id");
        current = new Snapshot(List.of(), List.of(new RemoteIssue("101", "6", "Ajustar menu", "https://gitlab.com/x/-/issues/6", "lider",
                List.of(), LocalDate.now(ZONE).plusDays(2), OffsetDateTime.now())), List.of());
        String repoId = JsonPath.read(post(alice, "/api/dev/projects/" + project + "/repos",
                "{\"accountId\":\"%s\",\"externalId\":\"101\"}".formatted(accountId)), "$.id");
        send("PUT", alice, "/api/dev/repos/" + repoId, "{\"importIssues\":true,\"closeIssues\":true}");
        UUID task = jdbc.sql("select id from tasks where user_id = :u").param("u", UUID.fromString(userId(alice)))
                .query(UUID.class).single();

        assertThat(send("PUT", alice, "/api/tasks/" + task + "/status", "{\"date\":\"%s\",\"status\":\"DONE\"}"
                .formatted(LocalDate.now(ZONE).plusDays(2)))).hasStatus(204);
        verify(gitlab, timeout(5000)).closeIssue(any(), eq(new IssueRef("101", "6")));
    }

    @Test
    void dailySummaryWithAi() throws Exception {
        String alice = registerAndGetBearer();
        when(gemini.enabled()).thenReturn(false);
        assertThat(send("POST", alice, "/api/dev/daily", null)).hasStatus(503);
        when(gemini.enabled()).thenReturn(true);
        when(gemini.generateJson(anyString(), anyList())).thenReturn("""
                {"ontem":["Abri o MR da tela de login"],"hoje":["Revisar o MR do Pedro"],"bloqueios":[]}""");
        String daily = body(send("POST", alice, "/api/dev/daily", null));
        assertThat(JsonPath.<String>read(daily, "$.text"))
                .isEqualTo("Ontem:\n- Abri o MR da tela de login\n\nHoje:\n- Revisar o MR do Pedro\n\nBloqueios:\n- Nenhum");
        when(gemini.generateJson(anyString(), anyList())).thenReturn("{\"nada\":true}");
        assertThat(send("POST", alice, "/api/dev/daily", null)).hasStatus(502);
    }

    private String connectGitLab(String bearer, String externalId) throws IntegrationException {
        String token = "glpat-" + UUID.randomUUID();
        when(gitlab.identify(eq("https://gitlab.com"), eq(token))).thenReturn(new Identity(externalId, "alice" + externalId, null, true));
        return JsonPath.read(post(bearer, "/api/integrations/gitlab", "{\"token\":\"%s\"}".formatted(token)), "$.id");
    }

    private List<String> types(String bearer) {
        return JsonPath.read(body(send("GET", bearer, "/api/notifications?page=0&size=50", null)), "$.items[*].type");
    }

    private static RemoteMr mr(String state, String pipeline, int comments, OffsetDateTime created, OffsetDateTime merged) {
        return new RemoteMr("101", "900", "31", "Tela de login", state, "https://gitlab.com/gertec/app/-/merge_requests/31", "alice",
                true, false, false, "5-login", pipeline, false, comments, List.of(new IssueRef("101", "5")), created, created, merged);
    }
}
