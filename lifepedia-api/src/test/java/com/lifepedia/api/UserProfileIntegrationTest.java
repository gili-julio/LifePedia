package com.lifepedia.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class UserProfileIntegrationTest extends ApiTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void registerRequiresValidUniqueUsername() {
        assertThat(register("", "a@" + UUID.randomUUID() + ".dev")).isEqualTo(400);
        assertThat(register("ab", "b" + UUID.randomUUID() + "@x.dev")).isEqualTo(400);
        assertThat(register(".joao", "c" + UUID.randomUUID() + "@x.dev")).isEqualTo(400);

        String username = newUsername();
        assertThat(register("@" + username.toUpperCase(), "d" + UUID.randomUUID() + "@x.dev")).isEqualTo(201);
        assertThat(register(username, "e" + UUID.randomUUID() + "@x.dev")).isEqualTo(409);
    }

    @Test
    void legacyAccountCompletesProfile() {
        String username = newUsername();
        String bearer = registerAndGetBearer("Maria", username);
        assertThat(send("GET", bearer, "/api/users/me", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.username").isEqualTo(username);
            assertThat(json).extractingPath("$.profileComplete").isEqualTo(true);
        });

        jdbc.update("update users set username = null where username = ?", username);
        assertThat(send("GET", bearer, "/api/users/me", null))
                .bodyJson().extractingPath("$.profileComplete").isEqualTo(false);

        String taken = newUsername();
        registerAndGetBearer("Outra", taken);
        assertThat(send("PUT", bearer, "/api/users/me", """
                {"name":"Maria","username":"%s"}""".formatted(taken))).hasStatus(409);

        String chosen = newUsername();
        assertThat(send("PUT", bearer, "/api/users/me", """
                {"name":"Maria Silva","username":"@%s"}""".formatted(chosen))).bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.name").isEqualTo("Maria Silva");
                    assertThat(json).extractingPath("$.username").isEqualTo(chosen);
                    assertThat(json).extractingPath("$.profileComplete").isEqualTo(true);
                });
    }

    private int register(String username, String email) {
        return mvc.post().uri("/api/auth/register").header(CSRF, "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Teste","username":"%s","email":"%s","password":"segredo123"}
                        """.formatted(username, email))
                .exchange().getResponse().getStatus();
    }
}
