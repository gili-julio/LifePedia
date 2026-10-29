package com.lifepedia.api;

import com.lifepedia.api.auth.GoogleIdentity;
import com.lifepedia.api.auth.GoogleTokenVerifier;
import com.lifepedia.api.shared.BusinessException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "lifepedia.google.client-id=test-client.apps.googleusercontent.com")
@AutoConfigureMockMvc
class GoogleAuthIntegrationTest extends ApiTestSupport {

    @MockitoBean
    GoogleTokenVerifier google;

    @Test
    void configExposesClientId() {
        assertThat(mvc.get().uri("/api/auth/config")).bodyJson().extractingPath("$.googleClientId")
                .isEqualTo("test-client.apps.googleusercontent.com");
    }

    @Test
    void firstGoogleLoginCreatesAccountWithoutPasswordNorUsername() {
        String email = newEmail();
        String credential = google("sub-" + UUID.randomUUID(), email, true, Instant.now());

        MvcTestResult first = googleLogin(credential, true);
        assertThat(first).hasStatusOk();
        assertThat(first.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=259");
        assertThat(first).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.user.profileComplete").isEqualTo(false);
            assertThat(json).extractingPath("$.user.termsAccepted").isEqualTo(false);
            assertThat(json).extractingPath("$.user.hasPassword").isEqualTo(false);
            assertThat(json).extractingPath("$.user.googleLinked").isEqualTo(true);
            assertThat(json).extractingPath("$.user.name").isEqualTo("Pessoa Google");
        });
        Cookie session = first.getResponse().getCookie("lifepedia_session");
        assertThat(mvc.get().uri("/api/users/me").cookie(session)).hasStatusOk();

        // Sem senha: login por senha nunca entra (nem com a senha "dummy" interna).
        assertThat(passwordLogin(email, "lifepedia-dummy-password")).hasStatus(401);
        assertThat(googleLogin(credential, false)).bodyJson().extractingPath("$.user.email").isEqualTo(email);
    }

    @Test
    void googleWithVerifiedSameEmailLinksExistingAccount() {
        String email = newEmail();
        register(email);
        MvcTestResult linked = googleLogin(google("sub-" + UUID.randomUUID(), email, true, Instant.now()), false);
        assertThat(linked).hasStatusOk();
        assertThat(linked).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.user.googleLinked").isEqualTo(true);
            assertThat(json).extractingPath("$.user.hasPassword").isEqualTo(true);
            assertThat(json).extractingPath("$.user.profileComplete").isEqualTo(true);
        });
        assertThat(linked.getResponse().getHeader(HttpHeaders.SET_COOKIE)).doesNotContain("Max-Age");

        // Outra conta Google com o mesmo e-mail não toma a conta já vinculada.
        assertThat(googleLogin(google("sub-" + UUID.randomUUID(), email, true, Instant.now()), false)).hasStatus(409);
    }

    @Test
    void unverifiedGoogleEmailDoesNotTakeOverExistingAccount() {
        String email = newEmail();
        register(email);
        assertThat(googleLogin(google("sub-" + UUID.randomUUID(), email, false, Instant.now()), false)).hasStatus(409);
    }

    @Test
    void invalidCredentialIsRejected() {
        when(google.verify("ruim")).thenThrow(new BusinessException(HttpStatus.UNAUTHORIZED, "inválido"));
        assertThat(googleLogin("ruim", false)).hasStatus(401);
    }

    @Test
    void linkAndUnlinkFromProfile() {
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        String credential = google("sub-" + UUID.randomUUID(), newEmail(), true, Instant.now());

        assertThat(send("PUT", alice, "/api/users/me/google", credentialJson(credential)))
                .bodyJson().extractingPath("$.googleLinked").isEqualTo(true);
        assertThat(send("PUT", bob, "/api/users/me/google", credentialJson(credential))).hasStatus(409);
        assertThat(send("PUT", alice, "/api/users/me/google", credentialJson(""))).hasStatus(400);

        assertThat(send("DELETE", alice, "/api/users/me/google", null))
                .bodyJson().extractingPath("$.googleLinked").isEqualTo(false);
        assertThat(send("PUT", bob, "/api/users/me/google", credentialJson(credential)))
                .bodyJson().extractingPath("$.googleLinked").isEqualTo(true);
    }

    @Test
    void googleOnlyAccountCannotUnlinkAndDeletesWithRecentGoogleLogin() {
        String sub = "sub-" + UUID.randomUUID();
        String email = newEmail();
        Cookie session = googleLogin(google(sub, email, true, Instant.now()), false).getResponse().getCookie("lifepedia_session");

        assertThat(mvc.delete().uri("/api/users/me/google").cookie(session).header(CSRF, "test")).hasStatus(400);

        String old = google(sub, email, true, Instant.now().minus(1, ChronoUnit.HOURS));
        String other = google("sub-" + UUID.randomUUID(), email, true, Instant.now());
        String fresh = google(sub, email, true, Instant.now());
        assertThat(deleteAccount(session, null)).hasStatus(400);
        assertThat(deleteAccount(session, old)).hasStatus(400);
        assertThat(deleteAccount(session, other)).hasStatus(400);
        assertThat(deleteAccount(session, fresh)).hasStatus(204);
        assertThat(mvc.get().uri("/api/users/me").cookie(session)).hasStatus(401);
    }

    private String google(String sub, String email, boolean verified, Instant issuedAt) {
        String credential = "cred-" + UUID.randomUUID();
        when(google.verify(credential)).thenReturn(new GoogleIdentity(sub, email, verified, "Pessoa Google", issuedAt));
        return credential;
    }

    private MvcTestResult googleLogin(String credential, boolean remember) {
        return mvc.post().uri("/api/auth/google").header(CSRF, "test").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"credential":"%s","remember":%s}
                        """.formatted(credential, remember)).exchange();
    }

    private MvcTestResult passwordLogin(String email, String password) {
        return mvc.post().uri("/api/auth/login").header(CSRF, "test").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)).exchange();
    }

    private void register(String email) {
        assertThat(mvc.post().uri("/api/auth/register").header(CSRF, "test").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Senha","username":"%s","email":"%s","password":"segredo123","acceptTerms":true}
                        """.formatted(newUsername(), email))).hasStatus(201);
    }

    private MvcTestResult deleteAccount(Cookie session, String googleCredential) {
        String json = googleCredential == null ? "{\"confirmation\":\"EXCLUIR\"}"
                : "{\"googleCredential\":\"%s\",\"confirmation\":\"EXCLUIR\"}".formatted(googleCredential);
        return mvc.delete().uri("/api/account").cookie(session).header(CSRF, "test")
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String credentialJson(String credential) {
        return "{\"credential\":\"%s\"}".formatted(credential);
    }

    private static String newEmail() {
        return "g-" + UUID.randomUUID() + "@lifepedia.dev";
    }
}
