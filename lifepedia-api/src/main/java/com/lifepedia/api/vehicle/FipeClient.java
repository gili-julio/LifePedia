package com.lifepedia.api.vehicle;

import com.lifepedia.api.config.VehicleProperties;
import com.lifepedia.api.shared.net.SafeHttpClient;
import com.lifepedia.api.shared.net.SafeHttpClient.ApiResponse;
import com.lifepedia.api.shared.net.SafeHttpClient.FetchException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Tabela FIPE pela API pública da parallelum (v2), via {@link SafeHttpClient} e fora de transação. Listas de marcas,
 * modelos e anos ficam 1 dia na memória; a cota vem dos cabeçalhos {@code x-ratelimit-*} da última resposta.
 */
@Component
public class FipeClient {

    public record Option(String code, String name) {
    }

    public record Price(String code, BigDecimal value, String brand, String model, Integer modelYear, String fuel, String month) {
    }

    /** @param limit consultas por dia; @param remaining o que sobra no dia; @param at quando foi lida */
    public record Quota(int limit, int remaining, Instant at) {
    }

    public static class FipeException extends Exception {
        public FipeException(String message) {
            super(message);
        }
    }

    static final Pattern CODE = Pattern.compile("\\d{1,10}");
    static final Pattern YEAR = Pattern.compile("\\d{4}-\\d{1,2}");
    private static final String BASE = "https://fipe.parallelum.com.br/api/v2/";
    private static final int MAX_BYTES = 512 * 1024;
    private static final int MAX_CACHE = 3_000;
    private static final Duration CACHE = Duration.ofDays(1);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private record Cached(List<Option> options, Instant at) {
    }

    private final SafeHttpClient http;
    private final VehicleProperties properties;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private volatile Quota quota;

    public FipeClient(SafeHttpClient http, VehicleProperties properties, Clock clock) {
        this.http = http;
        this.properties = properties;
        this.clock = clock;
    }

    public List<Option> brands(FipeKind kind) throws FipeException {
        return options(kind.path() + "/brands");
    }

    public List<Option> models(FipeKind kind, String brand) throws FipeException {
        return options(kind.path() + "/brands/" + code(brand) + "/models");
    }

    public List<Option> years(FipeKind kind, String brand, String model) throws FipeException {
        return options(kind.path() + "/brands/" + code(brand) + "/models/" + code(model) + "/years");
    }

    /** Valor do mês; vazio se a combinação não existe. */
    public Optional<Price> price(FipeKind kind, String brand, String model, String year) throws FipeException {
        if (year == null || !YEAR.matcher(year).matches()) throw new FipeException("Ano inválido.");
        JsonNode node = call(kind.path() + "/brands/" + code(brand) + "/models/" + code(model) + "/years/" + year);
        if (node == null || !node.isObject() || node.path("price").asString("").isBlank()) return Optional.empty();
        int year4 = node.path("modelYear").asInt(0);
        return Optional.of(new Price(node.path("codeFipe").asString(null), money(node.path("price").asString("")),
                node.path("brand").asString(null), node.path("model").asString(null), year4 > 0 ? year4 : null,
                node.path("fuel").asString(null), node.path("referenceMonth").asString(null)));
    }

    /** Última cota vista (nula antes da primeira chamada). */
    public Quota quota() {
        return quota;
    }

    /** "R$ 10.906,00" → 10906.00. */
    static BigDecimal money(String text) {
        String digits = text.replaceAll("[^0-9,]", "").replace(",", ".");
        if (digits.isEmpty()) return null;
        try {
            return new BigDecimal(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<Option> options(String path) throws FipeException {
        Instant now = Instant.now(clock);
        Cached cached = cache.get(path);
        if (cached != null && cached.at().plus(CACHE).isAfter(now)) return cached.options();
        JsonNode node = call(path);
        List<Option> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String code = item.path("code").asString("");
                String name = item.path("name").asString("");
                if (!code.isBlank() && !name.isBlank()) list.add(new Option(code, name));
            }
        }
        if (cache.size() >= MAX_CACHE) cache.clear();
        cache.put(path, new Cached(List.copyOf(list), now));
        return list;
    }

    /** Nulo em 404 (combinação inexistente). */
    private JsonNode call(String path) throws FipeException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        if (properties.hasToken()) headers.put("X-Subscription-Token", properties.fipeToken());
        ApiResponse response;
        try {
            response = http.exchange("GET", URI.create(BASE + path), headers, null, MAX_BYTES);
        } catch (FetchException e) {
            throw new FipeException("FIPE fora do ar.");
        }
        readQuota(response);
        if (response.status() == 404) return null;
        if (response.status() == 429) throw new FipeException("Cota diária da FIPE esgotada.");
        if (!response.ok()) throw new FipeException("FIPE respondeu " + response.status() + ".");
        try {
            return JSON.readTree(new String(response.body(), StandardCharsets.UTF_8));
        } catch (JacksonException e) {
            throw new FipeException("Resposta da FIPE inválida.");
        }
    }

    private void readQuota(ApiResponse response) {
        try {
            String limit = response.header("x-ratelimit-limit");
            String remaining = response.header("x-ratelimit-remaining");
            if (limit != null && remaining != null) {
                quota = new Quota(Integer.parseInt(limit.trim()), Integer.parseInt(remaining.trim()), Instant.now(clock));
            }
        } catch (NumberFormatException ignored) {
            // cabeçalho fora do padrão: mantém a última cota
        }
    }

    private static String code(String value) throws FipeException {
        if (value == null || !CODE.matcher(value).matches()) throw new FipeException("Código inválido.");
        return value;
    }
}
