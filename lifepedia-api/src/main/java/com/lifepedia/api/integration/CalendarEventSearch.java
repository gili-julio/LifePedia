package com.lifepedia.api.integration;

import com.lifepedia.api.search.SearchHit;
import com.lifepedia.api.search.SearchKind;
import com.lifepedia.api.search.SearchProvider;
import com.lifepedia.api.search.SearchQuery;
import com.lifepedia.api.search.SearchSql;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/** Eventos das agendas conectadas na busca geral. */
@Component
public class CalendarEventSearch implements SearchProvider {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcClient jdbc;
    private final Clock clock;

    public CalendarEventSearch(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Set<SearchKind> kinds() {
        return Set.of(SearchKind.EVENT);
    }

    @Override
    public List<SearchHit> search(SearchQuery query, SearchKind kind) {
        return new SearchSql(query).where("e.user_id = :userId").param("zone", clock.getZone().getId())
                .text("e.title", List.of("e.title"), List.of("e.location")).dates("(e.starts_at at time zone :zone)::date")
                .list(jdbc, "e.id, e.title, e.starts_at, e.all_day, e.location, (e.starts_at at time zone :zone)::date as day",
                        "calendar_events e", "e.starts_at", this::hit);
    }

    private SearchHit hit(ResultSet rs, int row) throws SQLException {
        LocalDate day = SearchSql.date(rs, "day");
        String when = rs.getBoolean("all_day") ? SearchHit.day(day) + " (dia todo)"
                : SearchHit.day(day) + " às " + HOUR.format(rs.getObject("starts_at", OffsetDateTime.class)
                        .atZoneSameInstant(clock.getZone()));
        return SearchHit.of(SearchKind.EVENT, SearchSql.uuid(rs, "id"), rs.getString("title"), "/agenda?dia=" + day,
                        rs.getDouble("score"))
                .subtitle(when).date(day).field("Quando", when).field("Local", rs.getString("location"))
                .build();
    }
}
