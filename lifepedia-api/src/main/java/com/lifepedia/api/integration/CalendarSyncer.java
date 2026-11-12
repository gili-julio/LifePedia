package com.lifepedia.api.integration;

import com.lifepedia.api.integration.google.GoogleCalendarClient;
import com.lifepedia.api.integration.google.GoogleCalendarClient.RemoteEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Copia os eventos do Google Agenda (7 dias atrás a 60 à frente) e apaga os que sumiram. */
@Component
class CalendarSyncer implements AccountSyncer {

    static final int PAST_DAYS = 7;
    static final int FUTURE_DAYS = 60;
    static final int MAX_EVENTS = 2000;

    private final GoogleCalendarClient google;
    private final CalendarEventRepository events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    CalendarSyncer(GoogleCalendarClient google, CalendarEventRepository events, Clock clock, PlatformTransactionManager transactions) {
        this.google = google;
        this.events = events;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    @Override
    public boolean supports(IntegrationProvider provider) {
        return provider == IntegrationProvider.GOOGLE;
    }

    @Override
    public int sync(Connection connection) throws IntegrationException {
        LocalDate today = LocalDate.now(clock);
        OffsetDateTime from = today.minusDays(PAST_DAYS).atStartOfDay(clock.getZone()).toOffsetDateTime();
        OffsetDateTime to = today.plusDays(FUTURE_DAYS).atStartOfDay(clock.getZone()).toOffsetDateTime();
        List<RemoteEvent> remote = google.events(connection.token(), from, to, clock.getZone());
        List<RemoteEvent> kept = remote.subList(0, Math.min(remote.size(), MAX_EVENTS));
        transaction.executeWithoutResult(status -> {
            Map<String, CalendarEvent> current = new HashMap<>();
            events.findByAccountId(connection.accountId()).forEach(e -> current.put(e.getExternalId(), e));
            Set<String> seen = kept.stream().map(RemoteEvent::id).collect(Collectors.toSet());
            events.deleteAll(current.values().stream().filter(e -> !seen.contains(e.getExternalId())).toList());
            for (RemoteEvent event : kept) {
                String key = event.id().length() <= 300 ? event.id() : event.id().substring(0, 300);
                CalendarEvent row = current.computeIfAbsent(key, k -> new CalendarEvent(connection.accountId(), connection.userId(), k));
                row.set(event.title(), event.start(), event.end(), event.allDay(), event.location(), event.meetUrl(), event.htmlUrl());
                events.save(row);
            }
        });
        return kept.size();
    }
}
