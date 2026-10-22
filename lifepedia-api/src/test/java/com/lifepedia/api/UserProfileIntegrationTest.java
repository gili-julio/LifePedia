package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import com.lifepedia.api.config.StorageProperties;
import com.lifepedia.api.user.UserImageStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class UserProfileIntegrationTest extends ApiTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StorageProperties storage;

    @Autowired
    UserImageStore userImages;

    @Autowired
    PlatformTransactionManager transactions;

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

    @Test
    void profileImageReplacesAndCleansUpFiles() {
        String alice = registerAndGetBearer();
        String other = registerAndGetBearer();
        String aliceId = userId(alice);

        assertThat(uploadImage(alice, "/api/users/me/image", "não é imagem".getBytes())).hasStatus(400);
        assertThat(uploadImage(alice, "/api/users/me/image", png(32))).hasStatus(400);

        String firstUrl = JsonPath.read(body(uploadImage(alice, "/api/users/me/image", png(800))), "$.imageUrl");
        assertThat(firstUrl).startsWith("/api/users/" + aliceId + "/image?v=");
        Path first = usersDir().resolve(imageKey(firstUrl) + ".jpg");
        assertThat(first).exists();
        assertThat(send("GET", other, firstUrl, null)).hasStatusOk().hasContentType("image/jpeg");
        assertThat(mvc.get().uri(firstUrl).header(CSRF, "test").exchange()).hasStatus(401);

        String secondUrl = JsonPath.read(body(uploadImage(alice, "/api/users/me/image", png(600))), "$.imageUrl");
        Path second = usersDir().resolve(imageKey(secondUrl) + ".jpg");
        assertThat(first).doesNotExist();
        assertThat(second).exists();
        assertThat(send("GET", alice, "/api/users/me", null)).bodyJson().extractingPath("$.imageUrl").isEqualTo(secondUrl);

        assertThat(send("DELETE", alice, "/api/users/me/image", null)).bodyJson().extractingPath("$.imageUrl").isNull();
        assertThat(second).doesNotExist();
        assertThat(send("GET", other, secondUrl, null)).hasStatus(404);

        String thirdUrl = JsonPath.read(body(uploadImage(alice, "/api/users/me/image", png(600))), "$.imageUrl");
        Path third = usersDir().resolve(imageKey(thirdUrl) + ".jpg");
        assertThat(send("DELETE", alice, "/api/account", """
                {"password":"segredo123","confirmation":"EXCLUIR"}""")).hasStatus(204);
        assertThat(third).doesNotExist();
    }

    @Test
    void imageSavedInFailedTransactionIsDeleted() {
        String[] key = new String[1];
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            key[0] = userImages.save(png(100));
            assertThat(usersDir().resolve(key[0] + ".jpg")).exists();
            status.setRollbackOnly();
        });
        assertThat(usersDir().resolve(key[0] + ".jpg")).doesNotExist();
    }

    private Path usersDir() {
        return storage.dir().resolve("users").toAbsolutePath().normalize();
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
