package com.lifepedia.api.delivery;

import com.lifepedia.api.config.DeliveryProperties;
import com.lifepedia.api.shared.net.SafeHttpClient;
import com.lifepedia.api.shared.net.SafeHttpClient.ApiResponse;
import com.lifepedia.api.shared.net.SafeHttpClient.FetchException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** API do 17TRACK (v2.4): registrar códigos, ler o rastreio e a cota. Sempre fora de transação. */
@Component
public class Track17Client {

    /** Até 40 códigos por chamada. */
    public static final int BATCH = 40;
    private static final String BASE = "https://api.17track.net/track/v2.4/";
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** @param rejected código → mensagem do 17TRACK */
    public record Registered(Set<String> accepted, Map<String, String> rejected) {
    }

    /**
     * @param total  rastreios do plano
     * @param used   já usados
     * @param remain restantes
     * @param today  usados hoje
     * @param daily  máximo por dia
     */
    public record Quota(long total, long used, long remain, long today, long daily) {
    }

    public static class TrackException extends Exception {
        public TrackException(String message) {
            super(message);
        }
    }

    private final DeliveryProperties properties;
    private final SafeHttpClient http;

    public Track17Client(DeliveryProperties properties, SafeHttpClient http) {
        this.properties = properties;
        this.http = http;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    /** Já registrado (por esta conta do 17TRACK) conta como aceito. */
    public Registered register(List<String> codes) throws TrackException {
        JsonNode data = call("register", numbers(codes));
        Set<String> accepted = new HashSet<>();
        Map<String, String> rejected = new HashMap<>();
        for (JsonNode item : data.path("accepted").values()) accepted.add(item.path("number").asString());
        for (JsonNode item : data.path("rejected").values()) {
            String number = item.path("number").asString();
            String message = item.path("error").path("message").asString("");
            if (message.toLowerCase(Locale.ROOT).contains("registered")) accepted.add(number);
            else rejected.put(number, message.length() > 200 ? message.substring(0, 200) : message);
        }
        return new Registered(accepted, rejected);
    }

    /** Código → {@code track_info} dos aceitos. */
    public Map<String, JsonNode> track(List<String> codes) throws TrackException {
        JsonNode data = call("gettrackinfo", numbers(codes));
        Map<String, JsonNode> result = new HashMap<>();
        for (JsonNode item : data.path("accepted").values()) {
            result.put(item.path("number").asString(), item.path("track_info"));
        }
        return result;
    }

    public Quota quota() throws TrackException {
        JsonNode data = call("getquota", JSON.createObjectNode());
        return new Quota(data.path("quota_total").asLong(), data.path("quota_used").asLong(),
                data.path("quota_remain").asLong(), data.path("today_used").asLong(), data.path("max_track_daily").asLong());
    }

    private JsonNode call(String path, JsonNode body) throws TrackException {
        if (!enabled()) throw new TrackException("Rastreio desligado.");
        try {
            ApiResponse response = http.exchange("POST", URI.create(BASE + path),
                    Map.of("17token", properties.apiKey(), "Content-Type", "application/json", "Accept", "application/json"),
                    JSON.writeValueAsBytes(body), MAX_BYTES);
            if (response.status() == 429) throw new TrackException("Muitas consultas ao 17TRACK. Tente daqui a pouco.");
            if (response.status() == 401 || response.status() == 403) throw new TrackException("Chave do 17TRACK recusada.");
            if (response.status() != 200) throw new TrackException("17TRACK respondeu HTTP " + response.status() + ".");
            JsonNode root = JSON.readTree(new String(response.body(), StandardCharsets.UTF_8));
            if (root.path("code").asInt(-1) != 0) {
                throw new TrackException("17TRACK recusou a consulta (" + root.path("code").asInt() + ").");
            }
            return root.path("data");
        } catch (FetchException e) {
            throw new TrackException("17TRACK indisponível.");
        } catch (JacksonException e) {
            throw new TrackException("Resposta do 17TRACK ilegível.");
        }
    }

    private static ArrayNode numbers(List<String> codes) {
        ArrayNode array = JSON.createArrayNode();
        for (String code : codes) array.addObject().put("number", code);
        return array;
    }
}
