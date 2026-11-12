package com.lifepedia.api.dev;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** Movimento de raia já feito para um MR (nunca se repete). */
@Entity
@Table(name = "dev_automations")
public class DevAutomation extends BaseEntity {

    public enum Event { OPENED, MERGED }

    @Column(name = "repo_id", nullable = false, updatable = false)
    private UUID repoId;

    @Column(name = "mr_id", nullable = false, length = 100, updatable = false)
    private String mrId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Event event;

    @Column(nullable = false, length = 500, updatable = false)
    private String issues;

    protected DevAutomation() {
    }

    public DevAutomation(UUID repoId, String mrId, Event event, String issues) {
        this.repoId = repoId;
        this.mrId = mrId;
        this.event = event;
        this.issues = issues.length() <= 500 ? issues : issues.substring(0, 500);
    }

    public String getMrId() {
        return mrId;
    }

    public Event getEvent() {
        return event;
    }
}
