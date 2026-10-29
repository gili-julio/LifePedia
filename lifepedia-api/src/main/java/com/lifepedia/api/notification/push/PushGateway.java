package com.lifepedia.api.notification.push;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** POST no serviço de push do navegador (só hosts da lista, validados antes em {@link PushService}). */
@Component
public class PushGateway {

    private volatile HttpClient client;

    /** @return status HTTP da resposta */
    public int post(String endpoint, byte[] body, String authorization, int ttlSeconds, boolean urgent)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", authorization)
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("TTL", String.valueOf(ttlSeconds))
                .header("Urgency", urgent ? "high" : "normal")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return client().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /** Criado no primeiro envio: com o push desligado nada é aberto. */
    private HttpClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(5))
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .build();
                }
            }
        }
        return client;
    }
}
