package com.lifepedia.api.integration;

import com.lifepedia.api.integration.IntegrationException.Kind;
import com.lifepedia.api.shared.net.SafeHttpClient;
import com.lifepedia.api.shared.net.SafeHttpClient.ApiResponse;
import com.lifepedia.api.shared.net.SafeHttpClient.FetchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Chamadas JSON aos serviços externos pelo {@link SafeHttpClient}; status vira {@link IntegrationException}. */
@Component
public class ApiCalls {

    private static final Logger log = LoggerFactory.getLogger(ApiCalls.class);
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final String USER_AGENT = "LifePedia/1.0";

    /** Resposta lida: corpo em JSON e cabeçalhos (nome em minúsculas). */
    public record Result(JsonNode body, Map<String, String> headers) {

        public String header(String name) {
            return headers.get(name);
        }
    }

    private final SafeHttpClient http;
    private final JsonMapper json;

    public ApiCalls(SafeHttpClient http, JsonMapper json) {
        this.http = http;
        this.json = json;
    }

    public Result get(URI url, Map<String, String> headers) throws IntegrationException {
        return call("GET", url, headers, null, null);
    }

    /** Corpo em JSON ({@code body} vira JSON) ou formulário ({@code form} pronto, já codificado). */
    public Result call(String method, URI url, Map<String, String> headers, Object body, String form) throws IntegrationException {
        Map<String, String> all = new LinkedHashMap<>(headers);
        all.putIfAbsent("User-Agent", USER_AGENT);
        all.putIfAbsent("Accept", "application/json");
        byte[] bytes = null;
        if (form != null) {
            all.put("Content-Type", "application/x-www-form-urlencoded");
            bytes = form.getBytes(StandardCharsets.UTF_8);
        } else if (body != null) {
            all.put("Content-Type", "application/json");
            bytes = json.writeValueAsBytes(body);
        }
        ApiResponse response;
        try {
            response = http.exchange(method, url, all, bytes, MAX_BYTES);
        } catch (FetchException e) {
            log.info("Serviço externo fora do ar ({}): {}", url.getHost(), e.getMessage());
            throw IntegrationException.of(Kind.UNAVAILABLE);
        }
        if (!response.ok()) throw failure(url, response);
        try {
            JsonNode node = response.body().length == 0 ? json.createObjectNode() : json.readTree(response.body());
            return new Result(node, response.headers());
        } catch (JacksonException e) {
            throw IntegrationException.of(Kind.INVALID);
        }
    }

    private static IntegrationException failure(URI url, ApiResponse response) {
        int status = response.status();
        log.info("Serviço externo respondeu {} ({})", status, url.getHost());
        if (status == 403 && "0".equals(response.header("x-ratelimit-remaining"))) return IntegrationException.of(Kind.RATE_LIMITED);
        return IntegrationException.of(switch (status) {
            case 401 -> Kind.AUTH;
            case 403 -> Kind.FORBIDDEN;
            case 404, 410 -> Kind.NOT_FOUND;
            case 429 -> Kind.RATE_LIMITED;
            default -> status >= 500 || status >= 300 && status < 400 ? Kind.UNAVAILABLE : Kind.INVALID;
        });
    }
}
