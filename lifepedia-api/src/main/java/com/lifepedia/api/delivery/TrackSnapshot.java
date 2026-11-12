package com.lifepedia.api.delivery;

import tools.jackson.databind.JsonNode;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Retrato do rastreio lido do {@code track_info} do 17TRACK (puro, sem rede).
 *
 * @param taxPending último evento pede pagamento de imposto/taxa de importação
 * @param events     do mais novo ao mais antigo, no máximo {@link #MAX_EVENTS}
 */
public record TrackSnapshot(DeliveryStatus status, String subStatus, boolean taxPending, Integer carrierKey,
                            String carrierName, String carrierUrl, OffsetDateTime latestAt, String latestText,
                            String latestLocation, String origin, LocalDate estimatedFrom, LocalDate estimatedTo,
                            List<TrackEvent> events) {

    public static final int MAX_EVENTS = 80;

    public record TrackEvent(OffsetDateTime at, String text, String location) {
    }

    public static TrackSnapshot parse(JsonNode info) {
        DeliveryStatus status = DeliveryStatus.of(text(info.path("latest_status").path("status"), 30));
        String subStatus = text(info.path("latest_status").path("sub_status"), 60);
        JsonNode provider = info.path("tracking").path("providers").path(0);
        List<TrackEvent> events = new ArrayList<>();
        for (JsonNode e : provider.path("events").values()) {
            TrackEvent event = event(e);
            if (event != null) events.add(event);
        }
        events.sort(Comparator.comparing(TrackEvent::at, Comparator.nullsLast(Comparator.reverseOrder())));
        if (events.size() > MAX_EVENTS) events = new ArrayList<>(events.subList(0, MAX_EVENTS));
        TrackEvent latest = event(info.path("latest_event"));
        if (latest == null && !events.isEmpty()) latest = events.getFirst();

        JsonNode carrier = provider.path("provider");
        Integer carrierKey = carrier.path("key").isNumber() ? carrier.path("key").asInt() : null;
        String homepage = text(carrier.path("homepage"), 300);
        if (homepage != null && !homepage.startsWith("https://") && !homepage.startsWith("http://")) homepage = null;
        JsonNode estimated = info.path("time_metrics").path("estimated_delivery_date");
        return new TrackSnapshot(status, subStatus, latest != null && taxPending(latest.text()), carrierKey,
                text(carrier.path("name"), 80), homepage,
                latest == null ? null : latest.at(), latest == null ? null : latest.text(),
                latest == null ? null : latest.location(), place(info.path("shipping_info").path("shipper_address")),
                date(estimated.path("from")), date(estimated.path("to")), List.copyOf(events));
    }

    /** Texto do evento pede pagamento (Correios: "Aguardando pagamento", "tributado"...). */
    static boolean taxPending(String text) {
        if (text == null) return false;
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        if (plain.contains("tributad")) return true;
        boolean payment = plain.contains("pagamento") || plain.contains("payment") || plain.contains("imposto")
                || plain.contains("taxa") || plain.contains("customs duty");
        boolean waiting = plain.contains("aguard") || plain.contains("pendente") || plain.contains("awaiting")
                || plain.contains("pending") || plain.contains("efetue") || plain.contains("realize");
        return payment && waiting;
    }

    private static TrackEvent event(JsonNode e) {
        if (e == null || e.isMissingNode() || e.isNull()) return null;
        String description = text(e.path("description_translation").path("description"), 300);
        if (description == null) description = text(e.path("description"), 300);
        OffsetDateTime at = time(e.path("time_iso"));
        if (at == null) at = time(e.path("time_utc"));
        if (description == null && at == null) return null;
        String location = text(e.path("location"), 160);
        if (location == null) location = place(e.path("address"));
        return new TrackEvent(at, description == null ? "" : description, location);
    }

    private static String place(JsonNode address) {
        String city = text(address.path("city"), 80);
        String state = text(address.path("state"), 40);
        String country = text(address.path("country"), 40);
        String text = String.join(", ", Stream.of(city, state, country).filter(Objects::nonNull).toList());
        return text.isEmpty() ? null : text;
    }

    private static OffsetDateTime time(JsonNode node) {
        String value = text(node, 40);
        if (value == null) return null;
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDate date(JsonNode node) {
        String value = text(node, 40);
        if (value == null || value.length() < 10) return null;
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String text(JsonNode node, int max) {
        if (node == null || node.isMissingNode() || node.isNull() || node.isObject() || node.isArray()) return null;
        String value = node.asString().trim();
        if (value.isEmpty()) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }
}
