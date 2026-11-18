package com.lifepedia.api.delivery;

import com.lifepedia.api.delivery.DeliveryTimeline.Settled;
import com.lifepedia.api.delivery.DeliveryTimeline.Step;
import com.lifepedia.api.delivery.TrackSnapshot.TrackEvent;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryTimelineTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-17T12:00:00-03:00");

    @Test
    void manualStepWinsUntilTrackingGoesFurther() {
        Step delivered = step(DeliveryStatus.DELIVERED, NOW.minusHours(2));
        List<TrackEvent> tracked = List.of(new TrackEvent(NOW.minusDays(1), "Em trânsito", "Recife"));

        Settled manual = DeliveryTimeline.settle(DeliveryStatus.IN_TRANSIT, tracked, List.of(delivered), null, NOW);
        assertThat(manual.status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(manual.deliveredAt()).isEqualTo(delivered.at());
        assertThat(manual.latestText()).isEqualTo("Entregue");

        Step transit = step(DeliveryStatus.IN_TRANSIT, NOW.minusDays(3));
        List<TrackEvent> out = List.of(new TrackEvent(NOW.minusHours(1), "Saiu para entrega", "Natal"));
        Settled tracking = DeliveryTimeline.settle(DeliveryStatus.OUT_FOR_DELIVERY, out, List.of(transit), null, NOW);
        assertThat(tracking.status()).isEqualTo(DeliveryStatus.OUT_FOR_DELIVERY);
        assertThat(tracking.latestText()).isEqualTo("Saiu para entrega");
        assertThat(tracking.deliveredAt()).isNull();

        Settled tie = DeliveryTimeline.settle(DeliveryStatus.IN_TRANSIT, tracked, List.of(transit), null, NOW);
        assertThat(tie.status()).isEqualTo(DeliveryStatus.IN_TRANSIT);
        assertThat(DeliveryTimeline.merge(tracked, List.of(transit)).getFirst().manual()).isFalse();
    }

    @Test
    void trackedDeliveryWithoutDateUsesTheSavedOne() {
        OffsetDateTime saved = NOW.minusDays(2);
        assertThat(DeliveryTimeline.settle(DeliveryStatus.DELIVERED, List.of(), List.of(), saved, NOW).deliveredAt()).isEqualTo(saved);
        assertThat(DeliveryTimeline.settle(DeliveryStatus.DELIVERED, List.of(), List.of(), null, NOW).deliveredAt()).isEqualTo(NOW);
    }

    @Test
    void guessesShopeeCodes() {
        assertThat(DeliveryCarrier.guess("BR263581416143Y")).contains(DeliveryCarrier.SHOPEE);
        assertThat(DeliveryCarrier.guess("LB123456789BR")).isEmpty();
        assertThat(DeliveryCarrier.wanted(2151, "BR263581416143Y")).isEqualTo(2151);
        assertThat(DeliveryCarrier.wanted(null, "NX123456789BR")).isNull();
    }

    private static Step step(DeliveryStatus status, OffsetDateTime at) {
        return new Step(UUID.randomUUID(), status, at, DeliveryTimeline.label(status), null);
    }
}
