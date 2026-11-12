package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import com.lifepedia.api.integration.IntegrationException;
import com.lifepedia.api.integration.IntegrationException.Kind;
import com.lifepedia.api.integration.IntegrationProvider;
import com.lifepedia.api.integration.IntegrationSyncService;
import com.lifepedia.api.integration.code.CodeHost.Identity;
import com.lifepedia.api.integration.code.CodeHost.Snapshot;
import com.lifepedia.api.integration.code.GitHubClient;
import com.lifepedia.api.integration.code.GitLabClient;
import com.lifepedia.api.integration.duolingo.DuolingoClient;
import com.lifepedia.api.integration.duolingo.DuolingoClient.Profile;
import com.lifepedia.api.integration.google.GoogleCalendarClient;
import com.lifepedia.api.integration.google.GoogleCalendarClient.RemoteEvent;
import com.lifepedia.api.integration.google.GoogleCalendarClient.Tokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/** Central de integrações: conectar com token, Duolingo, Google Agenda, falhas e isolamento (serviços externos são mocks). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class IntegrationIntegrationTest extends ApiTestSupport {

    @MockitoBean
    GitLabClient gitlab;

    @MockitoBean
    GitHubClient github;

    @MockitoBean
    GoogleCalendarClient google;

    @MockitoBean
    DuolingoClient duolingo;

    @Autowired
    IntegrationSyncService sync;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void providers() throws IntegrationException {
        when(gitlab.provider()).thenReturn(IntegrationProvider.GITLAB);
        when(github.provider()).thenReturn(IntegrationProvider.GITHUB);
        when(google.enabled()).thenReturn(true);
        when(gitlab.snapshot(any(), any(), any())).thenReturn(new Snapshot(List.of(), List.of(), List.of()));
    }

    @Test
    void connectsGitLabWithSealedToken() throws IntegrationException {
        String me = registerAndGetBearer();
        String raw = "glpat-" + UUID.randomUUID();
        when(gitlab.identify(eq("https://gitlab.empresa.com"), eq(raw)))
                .thenReturn(new Identity("42", "maria", LocalDate.now().plusDays(3), true));

        String created = post(me, "/api/integrations/gitlab", """
                {"baseUrl":"gitlab.empresa.com/","token":"%s","nickname":"Trabalho","color":"#123ABC"}""".formatted(raw));
        assertThat(created).doesNotContain(raw);
        assertThat(JsonPath.<String>read(created, "$.provider")).isEqualTo("GITLAB");
        assertThat(JsonPath.<String>read(created, "$.baseUrl")).isEqualTo("https://gitlab.empresa.com");
        assertThat(JsonPath.<String>read(created, "$.login")).isEqualTo("maria");
        assertThat(JsonPath.<String>read(created, "$.color")).isEqualTo("#123abc");
        String id = JsonPath.read(created, "$.id");

        String stored = jdbc.sql("select access_token from integration_accounts where id = :id").param("id", UUID.fromString(id))
                .query(String.class).single();
        assertThat(stored).startsWith("v1:").doesNotContain(raw);

        String overview = body(send("GET", me, "/api/integrations", null));
        assertThat(JsonPath.<List<String>>read(overview, "$.accounts[*].id")).containsExactly(id);
        assertThat(JsonPath.<List<Boolean>>read(overview, "$.providers[?(@.provider == 'GITLAB')].available")).containsExactly(true);
        assertThat(JsonPath.<List<Object>>read(body(send("GET", me, "/api/integrations/" + id + "/syncs", null)), "$[*]")).hasSize(1);

        // Mesmo usuário no mesmo servidor: só troca o token, sem conta nova.
        post(me, "/api/integrations/gitlab", """
                {"baseUrl":"https://gitlab.empresa.com","token":"%s"}""".formatted(raw));
        assertThat(JsonPath.<List<String>>read(body(send("GET", me, "/api/integrations", null)), "$.accounts[*].id")).hasSize(1);

        // Token vencendo: avisa uma vez.
        assertThat(sync.warnExpiring()).isGreaterThanOrEqualTo(1);
        assertThat(JsonPath.<List<String>>read(body(send("GET", me, "/api/notifications", null)), "$.items[*].type"))
                .contains("INTEGRATION_EXPIRING");
        assertThat(JsonPath.<List<String>>read(body(send("GET", me, "/api/notifications", null)),
                "$.items[?(@.type == 'INTEGRATION_EXPIRING')].id")).hasSize(1);
        sync.warnExpiring();
        assertThat(JsonPath.<List<String>>read(body(send("GET", me, "/api/notifications", null)),
                "$.items[?(@.type == 'INTEGRATION_EXPIRING')].id")).hasSize(1);
    }

    @Test
    void validatesAndRefuses() throws IntegrationException {
        String me = registerAndGetBearer();
        assertThat(send("POST", me, "/api/integrations/gitlab", "{\"token\":\"\"}")).hasStatus(400);
        assertThat(send("POST", me, "/api/integrations/gitlab", "{\"baseUrl\":\"http://gitlab.empresa.com\",\"token\":\"x\"}"))
                .hasStatus(400);
        assertThat(send("POST", me, "/api/integrations/gitlab", "{\"baseUrl\":\"https://gitlab.empresa.com:8443\",\"token\":\"x\"}"))
                .hasStatus(400);
        assertThat(send("POST", me, "/api/integrations/github", "{\"token\":\"x\",\"color\":\"azul\"}")).hasStatus(400);
        when(github.identify(isNull(), anyString())).thenThrow(IntegrationException.of(Kind.AUTH));
        assertThat(send("POST", me, "/api/integrations/github", "{\"token\":\"ruim\"}")).hasStatus(400)
                .bodyJson().extractingPath("$.detail").asString().contains("Token recusado");
        assertThat(send("POST", me, "/api/integrations/duolingo", "{\"username\":\"com espaço\"}")).hasStatus(400);
        assertThat(mvc.get().uri("/api/integrations")).hasStatus(401);
    }

    @Test
    void failingSyncMarksErrorAndNotifiesOnce() throws IntegrationException {
        String me = registerAndGetBearer();
        when(github.identify(isNull(), eq("ghp_ok"))).thenReturn(new Identity("7", "joao", null, true));
        String id = JsonPath.read(post(me, "/api/integrations/github", "{\"token\":\"ghp_ok\"}"), "$.id");
        assertThat(JsonPath.<String>read(body(send("GET", me, "/api/integrations", null)), "$.accounts[0].status")).isEqualTo("ACTIVE");

        jdbc.sql("insert into dev_projects (id, user_id, name, color, links, position, created_at, updated_at) "
                        + "values (:p, :u, 'Sebito', '#000000', '[]', 1, now(), now())")
                .param("p", UUID.randomUUID()).param("u", UUID.fromString(userId(me))).update();
        UUID project = jdbc.sql("select id from dev_projects where user_id = :u").param("u", UUID.fromString(userId(me)))
                .query(UUID.class).single();
        jdbc.sql("insert into dev_repos (id, project_id, user_id, account_id, external_id, full_name, web_url, import_issues, "
                        + "close_issues, rules_since, created_at, updated_at) values (gen_random_uuid(), :p, :u, :a, 'org/app', "
                        + "'org/app', 'https://github.com/org/app', true, false, now(), now(), now())")
                .param("p", project).param("u", UUID.fromString(userId(me))).param("a", UUID.fromString(id)).update();
        when(github.snapshot(any(), any(), any())).thenThrow(IntegrationException.of(Kind.AUTH));

        assertThat(sync.sync(UUID.fromString(id), false)).isFalse();
        assertThat(sync.sync(UUID.fromString(id), false)).isFalse();
        String overview = body(send("GET", me, "/api/integrations", null));
        assertThat(JsonPath.<String>read(overview, "$.accounts[0].status")).isEqualTo("ERROR");
        assertThat(JsonPath.<String>read(overview, "$.accounts[0].lastError")).contains("recusou");
        assertThat(JsonPath.<List<String>>read(overview, "$.accounts[0].projects")).containsExactly("Sebito");
        assertThat(JsonPath.<List<String>>read(body(send("GET", me, "/api/notifications", null)),
                "$.items[?(@.type == 'INTEGRATION_FAILED')].id")).hasSize(1);
        assertThat(JsonPath.<Integer>read(body(send("GET", me, "/api/integrations/summary", null)), "$.problems")).isEqualTo(1);
    }

    @Test
    void duolingoStreakAndAchievements() throws IntegrationException {
        String me = registerAndGetBearer();
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        when(duolingo.profile("maria_duo")).thenReturn(new Profile("999", "maria_duo", 31, 12_500, today));
        String created = post(me, "/api/integrations/duolingo", "{\"username\":\"maria_duo\"}");
        assertThat(JsonPath.<Integer>read(created, "$.duolingo.streak")).isEqualTo(31);
        assertThat(JsonPath.<Boolean>read(created, "$.duolingo.doneToday")).isTrue();

        // Ofensiva caiu: a maior fica guardada.
        when(duolingo.profile("maria_duo")).thenReturn(new Profile("999", "maria_duo", 2, 12_600, null));
        String id = JsonPath.read(created, "$.id");
        String synced = body(send("POST", me, "/api/integrations/" + id + "/sync", null));
        assertThat(JsonPath.<Integer>read(synced, "$.duolingo.bestStreak")).isEqualTo(31);
        assertThat(send("POST", me, "/api/integrations/" + id + "/sync", null)).hasStatus(429);

        String summary = body(send("GET", me, "/api/integrations/summary", null));
        assertThat(JsonPath.<Integer>read(summary, "$.connected")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(summary, "$.duolingo.streak")).isEqualTo(2);

        String achievements = body(send("GET", me, "/api/achievements", null));
        assertThat(JsonPath.<List<Integer>>read(achievements, "$[?(@.code == 'DUOLINGO_STREAK')].level")).containsExactly(2);
        assertThat(JsonPath.<List<Integer>>read(achievements, "$[?(@.code == 'DUOLINGO_XP')].level")).containsExactly(2);

        when(duolingo.profile("outra")).thenReturn(new Profile("1000", "outra", 1, 1, null));
        assertThat(send("POST", me, "/api/integrations/duolingo", "{\"username\":\"outra\"}")).hasStatus(409);
    }

    @Test
    void googleCalendarByOAuth() throws IntegrationException {
        String me = registerAndGetBearer();
        when(google.authorizeUrl(anyString(), anyString())).thenAnswer(call -> URI.create("https://accounts.google.com/auth?state="
                + URLEncoder.encode(call.getArgument(0), StandardCharsets.UTF_8)));
        String url = JsonPath.read(body(send("POST", me, "/api/integrations/google/start", null)), "$.url");
        String state = URLDecoder.decode(url.substring(url.indexOf("state=") + 6), StandardCharsets.UTF_8);
        when(google.exchange(eq("codigo"), eq("http://localhost:5173/api/integrations/google/callback")))
                .thenReturn(new Tokens("access", "refresh", 3600, "google-sub-1", "maria@gmail.com", true));
        OffsetDateTime start = LocalDate.now(ZoneId.of("America/Sao_Paulo")).atTime(10, 0).atZone(ZoneId.of("America/Sao_Paulo"))
                .toOffsetDateTime();
        when(google.events(eq("access"), any(), any(), any())).thenReturn(List.of(
                new RemoteEvent("primary:1", "Daily", start, start.plusMinutes(15), false, null, "https://meet.google.com/abc", null)));

        // Outra pessoa não usa o state de alguém.
        String other = registerAndGetBearer();
        assertThat(callback(other, "codigo", state).getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/integracoes?erro=google");

        MvcTestResult back = callback(me, "codigo", state);
        assertThat(back).hasStatus(302);
        assertThat(back.getResponse().getHeader(HttpHeaders.LOCATION)).startsWith("/integracoes?conectado=");
        String overview = body(send("GET", me, "/api/integrations", null));
        assertThat(JsonPath.<String>read(overview, "$.accounts[0].login")).isEqualTo("maria@gmail.com");

        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        String events = body(send("GET", me, "/api/integrations/calendar?from=" + today + "&to=" + today, null));
        assertThat(JsonPath.<List<String>>read(events, "$[*].title")).containsExactly("Daily");
        assertThat(JsonPath.<List<String>>read(events, "$[*].meetUrl")).containsExactly("https://meet.google.com/abc");
        assertThat(send("GET", me, "/api/integrations/calendar?from=" + today + "&to=" + today.plusDays(90), null)).hasStatus(400);
        assertThat(JsonPath.<List<Object>>read(body(send("GET", other, "/api/integrations/calendar?from=" + today + "&to=" + today, null)),
                "$[*]")).isEmpty();

        assertThat(callback(me, null, null).getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/integracoes?erro=google");
    }

    @Test
    void isolatedBetweenUsers() throws IntegrationException {
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        when(github.identify(isNull(), eq("ghp_alice"))).thenReturn(new Identity("1", "alice", null, true));
        String id = JsonPath.read(post(alice, "/api/integrations/github", "{\"token\":\"ghp_alice\"}"), "$.id");

        assertThat(send("PUT", bob, "/api/integrations/" + id, "{\"nickname\":\"x\",\"color\":\"#000000\"}")).hasStatus(404);
        assertThat(send("GET", bob, "/api/integrations/" + id + "/syncs", null)).hasStatus(404);
        assertThat(send("POST", bob, "/api/integrations/" + id + "/sync", null)).hasStatus(404);
        assertThat(send("DELETE", bob, "/api/integrations/" + id, null)).hasStatus(404);

        assertThat(send("PUT", alice, "/api/integrations/" + id, "{\"nickname\":\"Pessoal\",\"color\":\"#00FF00\"}")).hasStatusOk()
                .bodyJson().extractingPath("$.nickname").isEqualTo("Pessoal");
        when(github.identify(isNull(), eq("ghp_bob"))).thenReturn(new Identity("2", "bob", null, true));
        assertThat(send("PUT", alice, "/api/integrations/" + id + "/token", "{\"token\":\"ghp_bob\"}")).hasStatus(400);
        assertThat(send("DELETE", alice, "/api/integrations/" + id, null)).hasStatus(204);
        assertThat(JsonPath.<List<Object>>read(body(send("GET", alice, "/api/integrations", null)), "$.accounts")).isEmpty();
    }

    private MvcTestResult callback(String bearer, String code, String state) {
        var request = mvc.get().uri("/api/integrations/google/callback").header(HttpHeaders.AUTHORIZATION, bearer);
        if (code != null) request.param("code", code);
        if (state != null) request.param("state", state);
        return request.exchange();
    }
}
