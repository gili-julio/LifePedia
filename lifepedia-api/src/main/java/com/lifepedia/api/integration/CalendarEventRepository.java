package com.lifepedia.api.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {

    List<CalendarEvent> findByAccountId(UUID accountId);

    /** Eventos que cruzam o intervalo, do mais cedo. */
    @Query("""
            select e from CalendarEvent e where e.userId = :userId and e.startsAt < :to and e.endsAt > :from
             order by e.startsAt, e.title
            """)
    List<CalendarEvent> between(UUID userId, OffsetDateTime from, OffsetDateTime to);
}
