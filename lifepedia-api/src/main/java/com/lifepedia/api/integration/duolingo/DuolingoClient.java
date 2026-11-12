package com.lifepedia.api.integration.duolingo;

import com.lifepedia.api.integration.ApiCalls;
import com.lifepedia.api.integration.IntegrationException;
import com.lifepedia.api.integration.IntegrationException.Kind;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** Perfil público do Duolingo (sem API oficial nem login: pode mudar ou parar sem aviso). */
@Component
public class DuolingoClient {

    private static final String URL = "https://www.duolingo.com/2017-06-30/users?fields=users%7Bid,username,streak,totalXp,"
            + "streakData%7D&username=";

    /** @param lastLesson último dia da ofensiva (fuso da pessoa no Duolingo), nulo se não houver */
    public record Profile(String id, String username, int streak, long totalXp, LocalDate lastLesson) {
    }

    private final ApiCalls api;

    public DuolingoClient(ApiCalls api) {
        this.api = api;
    }

    public Profile profile(String username) throws IntegrationException {
        JsonNode users = api.get(URI.create(URL + URLEncoder.encode(username, StandardCharsets.UTF_8)),
                Map.of("User-Agent", "Mozilla/5.0 (compatible; LifePedia/1.0)")).body().path("users");
        if (users.isEmpty()) throw new IntegrationException(Kind.NOT_FOUND, "Não achamos esse usuário no Duolingo.");
        JsonNode user = users.get(0);
        String id = user.path("id").asString("");
        if (id.isBlank()) throw IntegrationException.of(Kind.INVALID);
        JsonNode current = user.path("streakData").path("currentStreak");
        String end = current.path("endDate").asString("");
        LocalDate last = null;
        try {
            if (end.length() >= 10) last = LocalDate.parse(end.substring(0, 10));
        } catch (DateTimeParseException ignored) {
            // Sem a data, o cartão só não diz se a lição de hoje foi feita.
        }
        return new Profile(id, user.path("username").asString(username), user.path("streak").asInt(0),
                user.path("totalXp").asLong(0), last);
    }
}
