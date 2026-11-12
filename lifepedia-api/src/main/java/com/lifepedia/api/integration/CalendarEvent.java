package com.lifepedia.api.integration;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Evento do Google Agenda copiado na última sincronização (só leitura). */
@Entity
@Table(name = "calendar_events")
public class CalendarEvent extends BaseEntity {

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "external_id", nullable = false, length = 300, updatable = false)
    private String externalId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    @Column(length = 300)
    private String location;

    @Column(name = "meet_url", length = 500)
    private String meetUrl;

    @Column(name = "html_url", length = 500)
    private String htmlUrl;

    protected CalendarEvent() {
    }

    public CalendarEvent(UUID accountId, UUID userId, String externalId) {
        this.accountId = accountId;
        this.userId = userId;
        this.externalId = externalId;
    }

    public void set(String title, OffsetDateTime startsAt, OffsetDateTime endsAt, boolean allDay, String location, String meetUrl,
                    String htmlUrl) {
        this.title = title;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.allDay = allDay;
        this.location = location;
        this.meetUrl = meetUrl;
        this.htmlUrl = htmlUrl;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getTitle() {
        return title;
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    public OffsetDateTime getEndsAt() {
        return endsAt;
    }

    public boolean isAllDay() {
        return allDay;
    }

    public String getLocation() {
        return location;
    }

    public String getMeetUrl() {
        return meetUrl;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }
}
