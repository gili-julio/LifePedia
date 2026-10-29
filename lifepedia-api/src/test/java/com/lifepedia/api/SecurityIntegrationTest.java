package com.lifepedia.api;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest extends ApiTestSupport {

    private String newEmail() {
        return "sec-" + UUID.randomUUID() + "@lifepedia.dev";
    }

    private MvcTestResult register(String email) {
        return mvc.post().uri("/api/auth/register").header(CSRF, "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Seg","username":"%s","email":"%s","password":"segredo123","acceptTerms":true}
                        """.formatted(newUsername(), email))
                .exchange();
    }

    private MvcTestResult login(String email, String password) {
        return mvc.post().uri("/api/auth/login").header(CSRF, "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password))
                .exchange();
    }

    @Test
    void browserLoginUsesHttpOnlyCookieAndHidesToken() {
        String email = newEmail();
        MvcTestResult result = register(email);
        assertThat(result).hasStatus(201);
        assertThat(body(result)).doesNotContain("\"token\"");

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("lifepedia_session=", "HttpOnly", "SameSite=Strict", "Secure", "Path=/api");

        Cookie session = result.getResponse().getCookie("lifepedia_session");
        assertThat(mvc.get().uri("/api/users/me").cookie(session)).hasStatusOk();
    }

    @Test
    void cookieAuthenticatedWritesRequireCustomHeader() {
        Cookie session = register(newEmail()).getResponse().getCookie("lifepedia_session");
        String payload = """
                {"name":"X","type":"EXPENSE","color":"#112233"}
                """;
        assertThat(mvc.post().uri("/api/finance/categories").cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content(payload)).hasStatus(403);
        assertThat(mvc.post().uri("/api/finance/categories").cookie(session).header(CSRF, "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON).content(payload)).hasStatus(201);
    }

    @Test
    void groupsNotificationsAndImagesRequireLogin() {
        for (String uri : new String[]{"/api/groups", "/api/groups/invites", "/api/notifications",
                "/api/groups/" + UUID.randomUUID() + "/image", "/api/groups/join/qualquer",
                "/api/groups/" + UUID.randomUUID() + "/finance/summary",
                "/api/groups/" + UUID.randomUUID() + "/planning",
                "/api/groups/" + UUID.randomUUID() + "/planning/items/" + UUID.randomUUID() + "/image"}) {
            assertThat(mvc.get().uri(uri)).as(uri).hasStatus(401);
        }
    }

    @Test
    void imageUploadWithCookieRequiresCustomHeader() {
        Cookie session = register(newEmail()).getResponse().getCookie("lifepedia_session");
        assertThat(mvc.put().uri("/api/groups/" + UUID.randomUUID() + "/image").cookie(session)
                .contentType(MediaType.MULTIPART_FORM_DATA).content("x")).hasStatus(403);
    }

    @Test
    void linkPreviewRequiresLoginAndCustomHeader() {
        String base = "/api/groups/" + UUID.randomUUID() + "/planning/";
        Cookie session = register(newEmail()).getResponse().getCookie("lifepedia_session");
        for (String[] route : new String[][]{{"link-preview", "{\"url\":\"https://loja.com.br\"}"},
                {"price-reference", "{\"name\":\"Fone JBL\"}"}}) {
            String uri = base + route[0];
            assertThat(mvc.post().uri(uri).header(CSRF, "test").contentType(MediaType.APPLICATION_JSON).content(route[1]))
                    .as(uri).hasStatus(401);
            assertThat(mvc.post().uri(uri).cookie(session).contentType(MediaType.APPLICATION_JSON).content(route[1]))
                    .as(uri).hasStatus(403);
        }
    }

    @Test
    void accountRoutesRequireLoginAndCustomHeader() {
        for (String uri : new String[]{"/api/account/export", "/api/account/export/spreadsheet", "/api/account/deletion-preview"}) {
            assertThat(mvc.get().uri(uri)).as(uri).hasStatus(401);
        }
        String deleteBody = "{\"password\":\"segredo123\",\"confirmation\":\"EXCLUIR\"}";
        assertThat(mvc.delete().uri("/api/account").header(CSRF, "test").contentType(MediaType.APPLICATION_JSON)
                .content(deleteBody)).hasStatus(401);
        MvcTestResult registered = register(newEmail());
        Cookie session = registered.getResponse().getCookie("lifepedia_session");
        assertThat(mvc.delete().uri("/api/account").cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content(deleteBody)).hasStatus(403);
        assertThat(mvc.post().uri("/api/account/import").cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).hasStatus(403);

        // Excluída a conta, o cookie antigo não entra mais.
        assertThat(mvc.delete().uri("/api/account").cookie(session).header(CSRF, "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON).content(deleteBody)).hasStatus(204);
        assertThat(mvc.get().uri("/api/users/me").cookie(session)).hasStatus(401);
    }

    @Test
    void loginWithoutCustomHeaderIsBlocked() {
        assertThat(mvc.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.c\",\"password\":\"x\"}")).hasStatus(403);
    }

    @Test
    void logoutExpiresCookie() {
        MvcTestResult result = mvc.post().uri("/api/auth/logout").header(CSRF, "test").exchange();
        assertThat(result).hasStatus(204);
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("lifepedia_session=", "Max-Age=0");
    }

    @Test
    void rememberMeKeepsCookieOtherwiseBrowserSessionCookie() {
        String email = newEmail();
        register(email);
        MvcTestResult remembered = mvc.post().uri("/api/auth/login").header(CSRF, "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"segredo123","remember":true}
                        """.formatted(email))
                .exchange();
        assertThat(remembered).hasStatusOk();
        assertThat(remembered.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=259");
        assertThat(remembered).bodyJson().extractingPath("$.remember").isEqualTo(true);

        MvcTestResult plain = login(email, "segredo123");
        assertThat(plain.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("lifepedia_session=").doesNotContain("Max-Age");
    }

    @Test
    void logoutRevokesTheSession() {
        Cookie session = register(newEmail()).getResponse().getCookie("lifepedia_session");
        assertThat(mvc.get().uri("/api/users/me").cookie(session)).hasStatusOk();
        assertThat(mvc.post().uri("/api/auth/logout").header(CSRF, "test").cookie(session)).hasStatus(204);
        assertThat(mvc.get().uri("/api/users/me").cookie(session)).hasStatus(401);

        String bearer = registerAndGetBearer();
        assertThat(send("GET", bearer, "/api/users/me", null)).hasStatusOk();
        assertThat(send("POST", bearer, "/api/auth/logout", null)).hasStatus(204);
        assertThat(send("GET", bearer, "/api/users/me", null)).hasStatus(401);
    }

    @Test
    void authConfigIsPublicAndGoogleOffWithoutClientId() {
        assertThat(mvc.get().uri("/api/auth/config").cookie(new Cookie("lifepedia_session", "lixo")))
                .bodyJson().extractingPath("$.googleClientId").isNull();
        assertThat(mvc.post().uri("/api/auth/google").header(CSRF, "test").contentType(MediaType.APPLICATION_JSON)
                .content("{\"credential\":\"x\"}")).hasStatus(404);
    }

    @Test
    void expiredOrInvalidCookieDoesNotBlockLogin() {
        String email = newEmail();
        register(email);
        assertThat(mvc.post().uri("/api/auth/login").header(CSRF, "test")
                .cookie(new Cookie("lifepedia_session", "lixo"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"segredo123"}
                        """.formatted(email))).hasStatusOk();
    }

    @Test
    void locksEmailAfterRepeatedWrongPasswords() {
        String email = newEmail();
        register(email);
        for (int i = 0; i < 5; i++) {
            assertThat(login(email, "senha-errada")).hasStatus(401);
        }
        // mesmo com a senha certa, fica bloqueado durante a janela
        assertThat(login(email, "segredo123")).hasStatus(429);
    }

    @Test
    void unknownEmailAndWrongPasswordLookTheSame() {
        String email = newEmail();
        register(email);
        MvcTestResult wrongPassword = login(email, "senha-errada");
        MvcTestResult unknownEmail = login(newEmail(), "senha-errada");
        assertThat(wrongPassword).hasStatus(401);
        assertThat(unknownEmail).hasStatus(401);
        assertThat(body(wrongPassword)).isEqualTo(body(unknownEmail));
    }
}
