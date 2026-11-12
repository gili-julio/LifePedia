package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminIntegrationTest extends ApiTestSupport {

    /** Mesmo e-mail de {@code lifepedia.admin.email} no perfil de teste. */
    private static final String FIXED_EMAIL = "admin-fixo@lifepedia.dev";
    private static final String PASSWORD = "segredo123";

    @Test
    void onlyAdminsReachAdminRoutes() {
        String user = registerAndGetBearer();
        for (String uri : List.of("/api/admin/overview", "/api/admin/users", "/api/admin/modules", "/api/admin/ai",
                "/api/admin/status", "/api/admin/audit", "/api/admin/announcements")) {
            assertThat(mvc.get().uri(uri)).as(uri).hasStatus(401);
            assertThat(send("GET", user, uri, null)).as(uri).hasStatus(403);
        }
        assertThat(send("PUT", user, "/api/admin/users/" + userId(user) + "/admin", "{\"admin\":true}")).hasStatus(403);
        assertThat(send("POST", user, "/api/admin/broadcast", "{\"title\":\"x\",\"body\":\"y\",\"audience\":\"ALL\"}"))
                .hasStatus(403);
        assertThat(JsonPath.<Boolean>read(body(send("GET", user, "/api/users/me", null)), "$.admin")).isFalse();
    }

    @Test
    void fixedAdminComesFromEnvironmentAndCannotLoseAccess() {
        String admin = fixedAdmin();
        assertThat(JsonPath.<Boolean>read(body(send("GET", admin, "/api/users/me", null)), "$.admin")).isTrue();
        String id = userId(admin);
        assertThat(send("PUT", admin, "/api/admin/users/" + id + "/admin", "{\"admin\":false}")).hasStatus(400);
        assertThat(send("PUT", admin, "/api/admin/users/" + id + "/blocked", "{\"blocked\":true}")).hasStatus(400);

        MvcTestResult page = send("GET", admin, "/api/admin/users?q=admin-fixo&filter=ADMIN", null);
        assertThat(page).hasStatusOk();
        assertThat(JsonPath.<List<Boolean>>read(body(page), "$.items[?(@.id == '" + id + "')].fixedAdmin")).containsExactly(true);
        assertThat(JsonPath.<List<Boolean>>read(body(page), "$.items[?(@.id == '" + id + "')].self")).containsExactly(true);
    }

    @Test
    void adminGrantsAndRevokesAccessImmediately() {
        String admin = fixedAdmin();
        String other = registerAndGetBearer();
        String otherId = userId(other);

        assertThat(send("PUT", admin, "/api/admin/users/" + otherId + "/admin", "{\"admin\":true}")).hasStatusOk();
        assertThat(send("GET", other, "/api/admin/overview", null)).hasStatusOk();
        // Admin comum também não tira o próprio acesso.
        assertThat(send("PUT", other, "/api/admin/users/" + otherId + "/admin", "{\"admin\":false}")).hasStatus(400);

        assertThat(send("PUT", admin, "/api/admin/users/" + otherId + "/admin", "{\"admin\":false}")).hasStatusOk();
        assertThat(send("GET", other, "/api/admin/overview", null)).hasStatus(403);

        String audit = body(send("GET", admin, "/api/admin/audit?size=100", null));
        assertThat(JsonPath.<List<String>>read(audit, "$.items[?(@.targetId == '" + otherId + "')].action"))
                .contains("ADMIN_GRANTED", "ADMIN_REVOKED");
        assertThat(send("PUT", admin, "/api/admin/users/" + otherId + "/admin", "{}")).hasStatus(400);
        assertThat(send("GET", admin, "/api/admin/users/" + UUID.randomUUID(), null)).hasStatus(404);
    }

    @Test
    void blockingEndsSessionsAndPreventsLogin() {
        String admin = fixedAdmin();
        String email = "bloq-" + UUID.randomUUID() + "@lifepedia.dev";
        String user = register(email);
        String userId = userId(user);

        assertThat(send("PUT", admin, "/api/admin/users/" + userId + "/blocked", "{\"blocked\":true}")).hasStatusOk();
        assertThat(send("GET", user, "/api/users/me", null)).hasStatus(401);
        assertThat(login(email)).hasStatus(403);
        assertThat(send("PUT", admin, "/api/admin/users/" + userId(admin) + "/blocked", "{\"blocked\":true}")).hasStatus(400);

        MvcTestResult blocked = send("GET", admin, "/api/admin/users?filter=BLOCKED&size=100", null);
        assertThat(JsonPath.<List<String>>read(body(blocked), "$.items[*].id")).contains(userId);

        assertThat(send("PUT", admin, "/api/admin/users/" + userId + "/blocked", "{\"blocked\":false}")).hasStatusOk();
        assertThat(login(email)).hasStatusOk();
    }

    @Test
    void revokingSessionsLogsUserOut() {
        String admin = fixedAdmin();
        String user = registerAndGetBearer();
        String userId = userId(user);
        post(user, "/api/finance/transactions", """
                {"description":"Café","amount":5,"type":"EXPENSE","date":"2026-10-01","categoryId":"%s"}
                """.formatted(categoryId(user, "EXPENSE", "Alimentação")));

        String detail = body(send("GET", admin, "/api/admin/users/" + userId, null));
        assertThat(JsonPath.<Integer>read(detail, "$.counts.transactions")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(detail, "$.activeSessions")).isEqualTo(1);
        assertThat(detail).doesNotContain("Café");

        assertThat(send("DELETE", admin, "/api/admin/users/" + userId + "/sessions", null)).hasStatus(204);
        assertThat(send("GET", user, "/api/users/me", null)).hasStatus(401);
        assertThat(send("DELETE", admin, "/api/admin/users/" + userId(admin) + "/sessions", null)).hasStatus(400);
    }

    @Test
    void announcementsReachEveryoneUntilEnded() {
        String admin = fixedAdmin();
        String user = registerAndGetBearer();
        assertThat(send("POST", admin, "/api/admin/announcements", "{\"message\":\" \",\"level\":\"INFO\"}")).hasStatus(400);
        assertThat(send("POST", admin, "/api/admin/announcements", """
                {"message":"Velho","level":"INFO","endsAt":"%s"}""".formatted(OffsetDateTime.now().minusHours(1)))).hasStatus(400);

        String created = post(admin, "/api/admin/announcements", """
                {"message":"Manutenção às 22h","level":"WARNING","endsAt":"%s"}""".formatted(OffsetDateTime.now().plusDays(1)));
        String id = JsonPath.read(created, "$.id");
        assertThat(JsonPath.<List<String>>read(body(send("GET", user, "/api/announcements", null)), "$[*].id")).contains(id);

        assertThat(send("DELETE", admin, "/api/admin/announcements/" + id, null)).hasStatus(204);
        assertThat(JsonPath.<List<String>>read(body(send("GET", user, "/api/announcements", null)), "$[*].id"))
                .doesNotContain(id);
        assertThat(send("GET", user, "/api/announcements", null)).hasStatusOk();
    }

    @Test
    void broadcastNotifiesActiveAccounts() {
        String admin = fixedAdmin();
        String user = registerAndGetBearer();
        assertThat(send("POST", admin, "/api/admin/broadcast", "{\"title\":\"\",\"body\":\"y\",\"audience\":\"ALL\"}"))
                .hasStatus(400);
        MvcTestResult sent = send("POST", admin, "/api/admin/broadcast",
                "{\"title\":\"Novidade\",\"body\":\"Painel novo\",\"audience\":\"ALL\"}");
        assertThat(sent).hasStatusOk();
        assertThat(JsonPath.<Integer>read(body(sent), "$.recipients")).isPositive();

        String notifications = body(send("GET", user, "/api/notifications", null));
        assertThat(JsonPath.<List<String>>read(notifications, "$.items[?(@.type == 'SYSTEM_MESSAGE')].data.title"))
                .contains("Novidade");
    }

    @Test
    void dashboardNumbersAndStatus() {
        String admin = fixedAdmin();
        String overview = body(send("GET", admin, "/api/admin/overview", null));
        assertThat(JsonPath.<Integer>read(overview, "$.totalUsers")).isPositive();
        assertThat(JsonPath.<List<Object>>read(overview, "$.signupsDaily")).hasSize(30);
        assertThat(JsonPath.<List<Object>>read(overview, "$.signupsWeekly")).hasSize(26);

        String modules = body(send("GET", admin, "/api/admin/modules", null));
        assertThat(JsonPath.<List<String>>read(modules, "$.modules[*].key")).contains("FINANCE", "TASKS", "AI_IMPORT", "PUSH");

        assertThat(send("GET", admin, "/api/admin/ai", null)).hasStatusOk();
        String status = body(send("GET", admin, "/api/admin/status", null));
        assertThat(JsonPath.<Boolean>read(status, "$.database.up")).isTrue();
        assertThat(JsonPath.<Boolean>read(status, "$.features.fixedAdmin")).isTrue();
        assertThat(status).doesNotContain(FIXED_EMAIL);
    }

    /** Conta do {@code ADMIN_EMAIL}: cadastra na 1ª vez, depois entra. */
    @Test
    void showsTheLimitsOfExternalServices() {
        String admin = fixedAdmin();
        assertThat(send("GET", admin, "/api/admin/limits", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$[?(@.key == 'TRACK17')].configured").asArray().containsExactly(false);
            assertThat(json).extractingPath("$[?(@.key == 'GEMINI_IMPORT')].period").asArray().containsExactly("DAY");
            assertThat(json).extractingPath("$[?(@.key == 'GOOGLE_CALENDAR')].limit").asArray().containsExactly(100);
        });
        assertThat(send("GET", registerAndGetBearer(), "/api/admin/limits", null)).hasStatus(403);
    }

    private String fixedAdmin() {
        MvcTestResult result = mvc.post().uri("/api/auth/register").header("X-Auth-Mode", "token").header(CSRF, "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Admin","username":"%s","email":"%s","password":"%s","acceptTerms":true}
                        """.formatted(newUsername(), FIXED_EMAIL, PASSWORD))
                .exchange();
        if (result.getResponse().getStatus() == 201) {
            assertThat(JsonPath.<Boolean>read(body(result), "$.user.admin")).isTrue();
        } else {
            result = login(FIXED_EMAIL);
            assertThat(result).hasStatusOk();
        }
        return "Bearer " + JsonPath.read(body(result), "$.token");
    }

    private String register(String email) {
        MvcTestResult result = mvc.post().uri("/api/auth/register").header("X-Auth-Mode", "token").header(CSRF, "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Bloq","username":"%s","email":"%s","password":"%s","acceptTerms":true}
                        """.formatted(newUsername(), email, PASSWORD))
                .exchange();
        assertThat(result).hasStatus(201);
        return "Bearer " + JsonPath.read(body(result), "$.token");
    }

    private MvcTestResult login(String email) {
        return mvc.post().uri("/api/auth/login").header("X-Auth-Mode", "token").header(CSRF, "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                .exchange();
    }
}
