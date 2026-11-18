package com.lifepedia.api.delivery;

import com.lifepedia.api.delivery.DeliveryTimeline.Event;
import com.lifepedia.api.delivery.DeliveryTimeline.Step;
import com.lifepedia.api.delivery.TrackSnapshot.TrackEvent;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;

/** Eventos do rastreio e passos manuais gravados em JSON na entrega, e a situação mostrada ({@link DeliveryTimeline}). */
@Component
class DeliverySteps {

    private static final TypeReference<List<TrackEvent>> TRACKED = new TypeReference<>() {
    };
    private static final TypeReference<List<Step>> STEPS = new TypeReference<>() {
    };

    private final JsonMapper json;

    DeliverySteps(JsonMapper json) {
        this.json = json;
    }

    List<TrackEvent> tracked(Delivery d) {
        return read(d.getEvents(), TRACKED);
    }

    List<Step> steps(Delivery d) {
        return read(d.getManualEvents(), STEPS);
    }

    void write(Delivery d, List<Step> steps) {
        d.setManualEvents(steps.isEmpty() ? null : json.writeValueAsString(steps));
    }

    String trackedJson(TrackSnapshot snapshot) {
        List<TrackEvent> events = snapshot.events();
        if (events.isEmpty() && snapshot.latestText() != null) {
            events = List.of(new TrackEvent(snapshot.latestAt(), snapshot.latestText(), snapshot.latestLocation()));
        }
        return json.writeValueAsString(events);
    }

    void settle(Delivery d, OffsetDateTime now) {
        d.settle(DeliveryTimeline.settle(d.getTrackedStatus(), tracked(d), steps(d), d.getDeliveredAt(), now));
    }

    List<Event> events(Delivery d) {
        return DeliveryTimeline.merge(tracked(d), steps(d));
    }

    private <T> List<T> read(String value, TypeReference<List<T>> type) {
        if (value == null) return List.of();
        try {
            return json.readValue(value, type);
        } catch (JacksonException e) {
            return List.of();
        }
    }
}
