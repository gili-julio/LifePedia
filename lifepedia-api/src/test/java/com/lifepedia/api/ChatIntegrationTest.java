package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import com.lifepedia.api.chat.ChatStreams;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ChatIntegrationTest extends ApiTestSupport {

    @MockitoSpyBean
    ChatStreams streams;

    @Test
    void membersChatEditDeleteReplyAndReadState() {
        String alice = registerAndGetBearer("Alice", newUsername());
        String bob = registerAndGetBearer("Bob", newUsername());
        String carol = registerAndGetBearer();
        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Casa"}"""), "$.id");
        join(alice, bob, groupId);
        String chat = "/api/groups/" + groupId + "/chat";

        String hello = JsonPath.read(post(alice, chat + "/messages", """
                {"body":"  Oi, gente!  "}"""), "$.id");
        assertThat(send("GET", bob, "/api/groups/chat/unread", null)).bodyJson().extractingPath("$['" + groupId + "']").isEqualTo(1);
        assertThat(send("GET", alice, "/api/groups/chat/unread", null)).bodyJson().isEqualTo("{}");

        MvcTestResult reply = send("POST", bob, chat + "/messages", """
                {"body":"Oi, Alice","replyToId":"%s"}""".formatted(hello));
        assertThat(reply).hasStatus(201);
        assertThat(reply).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.author.name").isEqualTo("Bob");
            assertThat(json).extractingPath("$.replyTo.body").isEqualTo("Oi, gente!");
            assertThat(json).extractingPath("$.replyTo.author.name").isEqualTo("Alice");
        });
        // Responder escreve no chat, então Bob leu tudo até ali.
        assertThat(send("GET", bob, "/api/groups/chat/unread", null)).bodyJson().isEqualTo("{}");

        assertThat(send("PUT", bob, chat + "/messages/" + hello, """
                {"body":"invadido"}""")).hasStatus(403);
        assertThat(send("PUT", alice, chat + "/messages/" + hello, """
                {"body":"Oi, pessoal!"}""")).bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.body").isEqualTo("Oi, pessoal!");
                    assertThat(json).extractingPath("$.editedAt").isNotNull();
                });
        assertThat(send("DELETE", alice, chat + "/messages/" + hello, null)).hasStatus(204);
        assertThat(send("PUT", alice, chat + "/messages/" + hello, """
                {"body":"volta"}""")).hasStatus(400);

        assertThat(send("GET", bob, chat + "/messages", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.items.length()").isEqualTo(2);
            assertThat(json).extractingPath("$.items[0].deleted").isEqualTo(true);
            assertThat(json).extractingPath("$.items[0].body").isNull();
            assertThat(json).extractingPath("$.items[1].replyTo.body").isNull();
            assertThat(json).extractingPath("$.hasMore").isEqualTo(false);
        });
        assertThat(send("GET", alice, "/api/groups/chat/unread", null)).bodyJson().extractingPath("$['" + groupId + "']").isEqualTo(1);
        assertThat(send("POST", alice, chat + "/read", null)).hasStatus(204);
        assertThat(send("GET", alice, "/api/groups/chat/unread", null)).bodyJson().isEqualTo("{}");

        // Fora do grupo: 404; validação: 400.
        assertThat(send("GET", carol, chat + "/messages", null)).hasStatus(404);
        assertThat(send("POST", carol, chat + "/messages", "{\"body\":\"oi\"}")).hasStatus(404);
        assertThat(send("POST", alice, chat + "/messages", "{\"body\":\"   \"}")).hasStatus(400);
        assertThat(send("POST", alice, chat + "/messages", "{\"body\":\"" + "a".repeat(2001) + "\"}")).hasStatus(400);
        assertThat(send("POST", alice, chat + "/messages", """
                {"body":"oi","replyToId":"%s"}""".formatted(UUID.randomUUID()))).hasStatus(400);
    }

    @Test
    void historyPagesWithCursor() {
        String alice = registerAndGetBearer();
        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Só eu"}"""), "$.id");
        String chat = "/api/groups/" + groupId + "/chat/messages";
        for (int i = 1; i <= 5; i++) post(alice, chat, "{\"body\":\"m" + i + "\"}");

        String first = body(send("GET", alice, chat + "?size=2", null));
        assertThat(JsonPath.<List<String>>read(first, "$.items[*].body")).containsExactly("m4", "m5");
        assertThat(JsonPath.<Boolean>read(first, "$.hasMore")).isTrue();
        String oldest = JsonPath.read(first, "$.items[0].id");
        String second = body(send("GET", alice, chat + "?size=3&before=" + oldest, null));
        assertThat(JsonPath.<List<String>>read(second, "$.items[*].body")).containsExactly("m1", "m2", "m3");
        assertThat(JsonPath.<Boolean>read(second, "$.hasMore")).isFalse();
    }

    @Test
    void streamRequiresLoginAndDeliversOnlyToMembersAfterCommit() {
        assertThat(mvc.get().uri("/api/groups/chat/stream")).hasStatus(401);
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Casa"}"""), "$.id");
        join(alice, bob, groupId);

        MvcTestResult stream = mvc.get().uri("/api/groups/chat/stream").header(HttpHeaders.AUTHORIZATION, bob).asyncExchange();
        assertThat(stream).hasStatusOk();
        assertThat(stream.getResponse().getContentType()).startsWith("text/event-stream");
        assertThat(stream.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");

        clearInvocations(streams);
        post(alice, "/api/groups/" + groupId + "/chat/messages", "{\"body\":\"oi\"}");
        Set<UUID> members = Set.of(UUID.fromString(userId(alice)), UUID.fromString(userId(bob)));
        verify(streams, timeout(2000)).onMessage(argThat(e -> Set.copyOf(e.recipients()).equals(members)
                && e.message().body().equals("oi")));

        clearInvocations(streams);
        assertThat(send("POST", alice, "/api/groups/" + groupId + "/chat/messages", "{\"body\":\"\"}")).hasStatus(400);
        verify(streams, never()).onMessage(any());
    }

    private void join(String admin, String member, String groupId) {
        String token = JsonPath.read(body(send("POST", admin, "/api/groups/" + groupId + "/invite-link", null)), "$.token");
        assertThat(send("POST", member, "/api/groups/join/" + token, null)).hasStatusOk();
    }
}
