package com.lifepedia.api.delivery;

import com.lifepedia.api.delivery.TrackSnapshot.TrackEvent;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Junta o rastreio e os passos lançados à mão (pura). A situação mostrada é a mais avançada das duas: o passo manual vale
 * até o rastreio trazer algo à frente; no empate, fica a do rastreio (que tem mais detalhe).
 */
public final class DeliveryTimeline {

    public static final int MAX_STEPS = 30;
    public static final Set<DeliveryStatus> MANUAL = EnumSet.of(DeliveryStatus.POSTED, DeliveryStatus.IN_TRANSIT,
            DeliveryStatus.OUT_FOR_DELIVERY, DeliveryStatus.PICKUP, DeliveryStatus.DELIVERED);
    private static final Map<DeliveryStatus, String> LABEL = Map.of(
            DeliveryStatus.POSTED, "Postado",
            DeliveryStatus.IN_TRANSIT, "Em trânsito",
            DeliveryStatus.OUT_FOR_DELIVERY, "Saiu para entrega",
            DeliveryStatus.PICKUP, "Disponível para retirada",
            DeliveryStatus.DELIVERED, "Entregue");
    private static final Comparator<OffsetDateTime> NEWEST = Comparator.nullsLast(Comparator.reverseOrder());

    /** Passo lançado à mão. */
    public record Step(UUID id, DeliveryStatus status, OffsetDateTime at, String text, String location) {
    }

    /**
     * Evento da linha do tempo, do mais novo ao mais antigo.
     *
     * @param id     só nos manuais (para apagar)
     * @param status só nos manuais
     */
    public record Event(UUID id, OffsetDateTime at, String text, String location, boolean manual, DeliveryStatus status) {
    }

    public record Settled(DeliveryStatus status, OffsetDateTime latestAt, String latestText, String latestLocation,
                          OffsetDateTime deliveredAt) {
    }

    private DeliveryTimeline() {
    }

    public static String label(DeliveryStatus status) {
        return LABEL.getOrDefault(status, status.name());
    }

    /**
     * @param tracked       situação do rastreio
     * @param trackedEvents do mais novo ao mais antigo
     * @param deliveredAt   data de entrega já gravada (rastreio entregue sem data no evento)
     */
    public static Settled settle(DeliveryStatus tracked, List<TrackEvent> trackedEvents, List<Step> steps,
                                 OffsetDateTime deliveredAt, OffsetDateTime now) {
        Step top = steps.stream()
                .max(Comparator.comparingInt((Step s) -> s.status().stage()).thenComparing(Step::at)).orElse(null);
        boolean manual = top != null && top.status().stage() > tracked.stage();
        DeliveryStatus status = manual ? top.status() : tracked;
        List<Event> events = merge(trackedEvents, steps);
        Event latest = events.isEmpty() ? null : events.getFirst();
        OffsetDateTime delivered = null;
        if (status == DeliveryStatus.DELIVERED) {
            TrackEvent last = trackedEvents.isEmpty() ? null : trackedEvents.getFirst();
            delivered = manual ? top.at() : last != null && last.at() != null ? last.at() : deliveredAt != null ? deliveredAt : now;
        }
        return new Settled(status, latest == null ? null : latest.at(), latest == null ? null : latest.text(),
                latest == null ? null : latest.location(), delivered);
    }

    public static List<Event> merge(List<TrackEvent> trackedEvents, List<Step> steps) {
        List<Event> events = new ArrayList<>(trackedEvents.size() + steps.size());
        trackedEvents.forEach(e -> events.add(new Event(null, e.at(), e.text(), e.location(), false, null)));
        steps.forEach(s -> events.add(new Event(s.id(), s.at(), s.text(), s.location(), true, s.status())));
        events.sort(Comparator.comparing(Event::at, NEWEST));
        return events;
    }
}
