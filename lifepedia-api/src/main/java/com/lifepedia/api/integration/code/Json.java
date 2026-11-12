package com.lifepedia.api.integration.code;

import tools.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Leitura tolerante das respostas dos serviços (campo ausente ou nulo vira nulo). */
final class Json {

    private Json() {
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

    /** Texto cortado em {@code max} caracteres. */
    static String text(JsonNode node, String field, int max) {
        String value = text(node, field);
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    static OffsetDateTime time(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static LocalDate date(String value) {
        if (value == null || value.length() < 10) return null;
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : array) {
            String value = item.isString() ? item.asString() : text(item, "name");
            if (value != null && !value.isBlank()) values.add(value);
        }
        return values;
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
