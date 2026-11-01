package com.lifepedia.api.shared.ai;

import com.lifepedia.api.config.AiProperties;
import com.lifepedia.api.shared.ai.GeminiClient.AiException;
import com.lifepedia.api.shared.ai.GeminiClient.Attachment;
import com.lifepedia.api.shared.ai.GeminiClient.Failure;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiClientTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> key = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String answer = "{}";
    private volatile long delayMillis;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            path.set(exchange.getRequestURI().toString());
            key.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            sleep();
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsDocumentsBeforeThePromptAndReadsTheText() throws Exception {
        answer = """
                {"candidates":[{"finishReason":"STOP","content":{"parts":[
                  {"text":"pensando","thought":true},{"text":"{\\"items\\":"},{"text":"[]}"}]}}]}""";
        String text = client(Duration.ofSeconds(5)).generateJson("Leia", List.of(new Attachment("application/pdf", new byte[]{1, 2, 3})));

        assertThat(text).isEqualTo("{\"items\":[]}");
        assertThat(path.get()).isEqualTo("/v1beta/models/gemini-teste:generateContent");
        assertThat(key.get()).isEqualTo("chave-teste");
        JsonNode parts = JSON.readTree(body.get()).path("contents").path(0).path("parts");
        assertThat(parts.path(0).path("inlineData").path("mimeType").asString()).isEqualTo("application/pdf");
        assertThat(parts.path(0).path("inlineData").path("data").asString())
                .isEqualTo(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}));
        assertThat(parts.path(1).path("text").asString()).isEqualTo("Leia");
        assertThat(parts.path(1).has("inlineData")).isFalse();
        assertThat(JSON.readTree(body.get()).path("generationConfig").path("responseMimeType").asString()).isEqualTo("application/json");
    }

    @Test
    void mapsStatusToFailure() {
        assertFailure(400, "{}", Failure.REJECTED);
        assertFailure(429, "{}", Failure.BUSY);
        assertFailure(503, "{}", Failure.BUSY);
        assertFailure(403, "{}", Failure.FAILED);
        assertFailure(200, "não é json", Failure.FAILED);
    }

    @Test
    void mapsAnswerToFailure() {
        assertFailure(200, """
                {"promptFeedback":{"blockReason":"SAFETY"}}""", Failure.BLOCKED);
        assertFailure(200, """
                {"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[{"text":"{\\"items\\":["}]}}]}""", Failure.TOO_LONG);
        assertFailure(200, """
                {"candidates":[{"finishReason":"SAFETY"}]}""", Failure.BLOCKED);
        assertFailure(200, """
                {"candidates":[{"finishReason":"STOP","content":{"parts":[]}}]}""", Failure.FAILED);
    }

    @Test
    void respectsTheTimeout() {
        delayMillis = 2000;
        assertThatThrownBy(() -> client(Duration.ofMillis(200)).generateJson("Leia", List.of()))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.failure()).isEqualTo(Failure.TIMEOUT));
    }

    @Test
    void offWithoutKey() {
        assertThat(new GeminiClient(new AiProperties(" ", "m", Duration.ofSeconds(1), 1, "http://x")).enabled()).isFalse();
    }

    private void assertFailure(int code, String response, Failure failure) {
        status = code;
        answer = response;
        assertThatThrownBy(() -> client(Duration.ofSeconds(5)).generateJson("Leia", List.of()))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.failure()).isEqualTo(failure));
    }

    private GeminiClient client(Duration timeout) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta";
        return new GeminiClient(new AiProperties("chave-teste", "gemini-teste", timeout, 10, url));
    }

    private void sleep() {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
