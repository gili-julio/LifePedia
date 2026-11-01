package com.lifepedia.api.shared.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifepedia.api.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Chamadas ao Gemini com a chave do ambiente. Documentos e respostas nunca vão para o log, só o status. */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** Máximo aceito pelos modelos Flash: extrato longo não pode cortar a resposta no meio. */
    private static final int MAX_OUTPUT_TOKENS = 65_536;

    public record Attachment(String mimeType, byte[] data) {
    }

    public enum Failure { TIMEOUT, BUSY, REJECTED, BLOCKED, TOO_LONG, FAILED }

    public static class AiException extends Exception {
        private final Failure failure;

        public AiException(Failure failure) {
            super(failure.name());
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }

    record Body(List<Content> contents, Generation generationConfig) {
    }

    record Content(List<Part> parts) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Part(String text, Inline inlineData) {
    }

    record Inline(String mimeType, byte[] data) {
    }

    record Generation(String responseMimeType, int maxOutputTokens) {
    }

    private final AiProperties properties;
    private volatile HttpClient client;

    public GeminiClient(AiProperties properties) {
        this.properties = properties;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    /** Lê os anexos seguindo o prompt e devolve o JSON gerado pelo modelo. */
    public String generateJson(String prompt, List<Attachment> attachments) throws AiException {
        List<Part> parts = new ArrayList<>();
        for (Attachment attachment : attachments) {
            parts.add(new Part(null, new Inline(attachment.mimeType(), attachment.data())));
        }
        parts.add(new Part(prompt, null));
        Body body = new Body(List.of(new Content(parts)), new Generation("application/json", MAX_OUTPUT_TOKENS));

        URI url = URI.create(properties.baseUrl() + "/models/" + properties.model() + ":generateContent");
        HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(properties.timeout())
                .header("x-goog-api-key", properties.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body)))
                .build();
        try {
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new AiException(failureOf(response.statusCode()));
            return text(JSON.readTree(response.body()));
        } catch (HttpTimeoutException e) {
            throw new AiException(Failure.TIMEOUT);
        } catch (IOException | JacksonException e) {
            log.warn("Falha ao chamar o Gemini: {}", e.getClass().getSimpleName());
            throw new AiException(Failure.FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException(Failure.FAILED);
        }
    }

    /** Texto do primeiro candidato, sem as partes de raciocínio do modelo. */
    static String text(JsonNode root) throws AiException {
        if (!root.path("promptFeedback").path("blockReason").isMissingNode()) throw new AiException(Failure.BLOCKED);
        JsonNode candidate = root.path("candidates").path(0);
        String finish = candidate.path("finishReason").asString("");
        if (finish.equals("MAX_TOKENS")) throw new AiException(Failure.TOO_LONG);
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false)) text.append(part.path("text").asString(""));
        }
        if (!text.isEmpty()) return text.toString();
        throw new AiException(finish.equals("STOP") || finish.isEmpty() ? Failure.FAILED : Failure.BLOCKED);
    }

    private static Failure failureOf(int status) {
        log.warn("Gemini respondeu {}", status);
        return switch (status) {
            case 400 -> Failure.REJECTED;
            case 408, 504 -> Failure.TIMEOUT;
            case 429, 500, 503 -> Failure.BUSY;
            default -> Failure.FAILED;
        };
    }

    /** Criado na primeira leitura: sem chave nada é aberto. */
    private HttpClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .build();
                }
            }
        }
        return client;
    }
}
