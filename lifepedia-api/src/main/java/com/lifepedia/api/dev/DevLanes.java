package com.lifepedia.api.dev;

import com.lifepedia.api.dev.DevDtos.Lane;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Raias acompanhadas de um repositório (JSON em {@code dev_repos.lanes}) e em qual delas a issue está. */
@Component
public class DevLanes {

    static final int MAX_LANES = 8;
    private static final TypeReference<List<Lane>> LANES = new TypeReference<>() {
    };

    private final JsonMapper json;

    public DevLanes(JsonMapper json) {
        this.json = json;
    }

    public List<Lane> read(DevRepo repo) {
        if (repo.getLanes() == null || repo.getLanes().isBlank()) return List.of();
        try {
            return json.readValue(repo.getLanes(), LANES);
        } catch (JacksonException e) {
            return List.of();
        }
    }

    /** JSON das raias ou nulo sem nenhuma. */
    public String write(List<Lane> lanes) {
        return lanes.isEmpty() ? null : json.writeValueAsString(lanes);
    }

    /** Raia mais adiantada (a última na ordem) entre as etiquetas da issue; nula = fora das raias. */
    public static Lane current(List<String> labels, List<Lane> lanes) {
        Lane found = null;
        for (Lane lane : lanes) {
            if (labels.stream().anyMatch(lane.label()::equalsIgnoreCase)) found = lane;
        }
        return found;
    }

    /** Dias corridos desde {@code since} até {@code today} no fuso. */
    public static int days(OffsetDateTime since, LocalDate today, ZoneId zone) {
        return (int) Math.max(0, ChronoUnit.DAYS.between(since.atZoneSameInstant(zone).toLocalDate(), today));
    }
}
