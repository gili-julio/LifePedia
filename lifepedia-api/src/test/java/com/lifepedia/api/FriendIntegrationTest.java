package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class FriendIntegrationTest extends ApiTestSupport {

    @Test
    void requestAcceptProfileAndRemove() {
        String bobName = newUsername();
        String alice = registerAndGetBearer("Alice", newUsername());
        String bob = registerAndGetBearer("Bob", bobName);
        String carol = registerAndGetBearer("Carol", newUsername());
        String aliceId = userId(alice);
        String bobId = userId(bob);

        String search = "/api/friends/candidates?q=@" + bobName.substring(0, 8);
        assertThat(send("GET", alice, search, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$[0].id").isEqualTo(bobId);
            assertThat(json).extractingPath("$[0].status").isNull();
        });

        assertThat(send("POST", alice, "/api/friends/requests", """
                {"userId":"%s"}""".formatted(aliceId))).hasStatus(400);
        assertThat(send("POST", alice, "/api/friends/requests", """
                {"userId":"%s"}""".formatted(bobId))).bodyJson().extractingPath("$.status").isEqualTo("PENDING");
        assertThat(send("POST", alice, "/api/friends/requests", """
                {"userId":"%s"}""".formatted(bobId))).hasStatus(400);
        assertThat(send("GET", alice, search, null)).bodyJson().extractingPath("$[0].status").isEqualTo("SENT");

        assertThat(send("GET", bob, "/api/notifications", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.items[0].type").isEqualTo("FRIEND_REQUEST");
            assertThat(json).extractingPath("$.items[0].data.actorName").isEqualTo("Alice");
        });
        assertThat(send("GET", bob, "/api/friends/requests/count", null)).bodyJson().extractingPath("$.count").isEqualTo(1);
        String requestId = JsonPath.read(body(send("GET", bob, "/api/friends/requests", null)), "$.received[0].id");
        assertThat(JsonPath.<List<?>>read(body(send("GET", alice, "/api/friends/requests", null)), "$.sent")).hasSize(1);

        // Só quem recebeu aceita; ninguém de fora vê o perfil.
        assertThat(send("POST", alice, "/api/friends/requests/" + requestId + "/accept", null)).hasStatus(404);
        assertThat(send("POST", carol, "/api/friends/requests/" + requestId + "/accept", null)).hasStatus(404);
        assertThat(send("GET", alice, "/api/friends/" + bobId, null)).hasStatus(404);
        assertThat(send("POST", bob, "/api/friends/requests/" + requestId + "/accept", null)).hasStatus(204);

        assertThat(send("GET", alice, "/api/notifications", null)).bodyJson()
                .extractingPath("$.items[0].type").isEqualTo("FRIEND_ACCEPTED");
        assertThat(send("GET", alice, "/api/friends", null)).bodyJson().extractingPath("$[0].user.id").isEqualTo(bobId);
        assertThat(send("GET", bob, "/api/friends", null)).bodyJson().extractingPath("$[0].user.id").isEqualTo(aliceId);

        // Conquistas: amigos não veem as marcadas como só do dono.
        String task = JsonPath.read(post(bob, "/api/tasks", """
                {"title":"Ler"}"""), "$.id");
        assertThat(send("PUT", bob, "/api/tasks/" + task + "/status", """
                {"date":null,"status":"DONE"}""")).hasStatus(204);
        String profile = body(send("GET", alice, "/api/friends/" + bobId, null));
        assertThat(JsonPath.<String>read(profile, "$.user.name")).isEqualTo("Bob");
        assertThat(JsonPath.<List<String>>read(profile, "$.achievements[*].code"))
                .contains("TASKS_DONE", "FRIENDS").doesNotContain("TRANSACTIONS");
        assertThat(JsonPath.<List<Integer>>read(profile, "$.achievements[?(@.code == 'TASKS_DONE')].level")).containsExactly(1);
        assertThat(JsonPath.<List<Integer>>read(profile, "$.achievements[?(@.code == 'FRIENDS')].value")).containsExactly(1);
        assertThat(JsonPath.<List<String>>read(body(send("GET", bob, "/api/achievements", null)), "$[*].code"))
                .contains("TRANSACTIONS");

        assertThat(send("DELETE", carol, "/api/friends/" + bobId, null)).hasStatus(404);
        assertThat(send("DELETE", alice, "/api/friends/" + bobId, null)).hasStatus(204);
        assertThat(JsonPath.<List<?>>read(body(send("GET", bob, "/api/friends", null)), "$")).isEmpty();
    }

    @Test
    void crossedRequestsBecomeFriendsAndDismiss() {
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        String carol = registerAndGetBearer();
        String aliceId = userId(alice);
        String bobId = userId(bob);

        send("POST", alice, "/api/friends/requests", """
                {"userId":"%s"}""".formatted(bobId));
        assertThat(send("POST", bob, "/api/friends/requests", """
                {"userId":"%s"}""".formatted(aliceId))).bodyJson().extractingPath("$.status").isEqualTo("ACCEPTED");
        assertThat(send("GET", alice, "/api/friends/" + bobId, null)).hasStatusOk();

        // Cancelar (quem enviou) e recusar (quem recebeu) apagam o pedido; terceiro não mexe.
        send("POST", carol, "/api/friends/requests", """
                {"userId":"%s"}""".formatted(aliceId));
        String requestId = JsonPath.read(body(send("GET", alice, "/api/friends/requests", null)), "$.received[0].id");
        assertThat(send("DELETE", bob, "/api/friends/requests/" + requestId, null)).hasStatus(404);
        assertThat(send("DELETE", alice, "/api/friends/requests/" + requestId, null)).hasStatus(204);
        assertThat(JsonPath.<List<?>>read(body(send("GET", carol, "/api/friends/requests", null)), "$.sent")).isEmpty();
        assertThat(send("POST", carol, "/api/friends/requests", """
                {"userId":"%s"}""".formatted(aliceId))).hasStatusOk();

        assertThat(mvc.get().uri("/api/friends").exchange()).hasStatus(401);
        assertThat(mvc.get().uri("/api/achievements").exchange()).hasStatus(401);
    }
}
