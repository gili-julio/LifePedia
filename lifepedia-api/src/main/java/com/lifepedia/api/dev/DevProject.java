package com.lifepedia.api.dev;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Projeto de trabalho (ex.: uma empresa) com atalhos em JSON e a conta Google das reuniões. */
@Entity
@Table(name = "dev_projects")
public class DevProject extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 7)
    private String color;

    @Column(length = 300)
    private String description;

    @Column(nullable = false, length = 8000)
    private String links;

    @Column(name = "calendar_account_id")
    private UUID calendarAccountId;

    @Column(nullable = false)
    private int position;

    protected DevProject() {
    }

    public DevProject(UUID userId, int position) {
        this.userId = userId;
        this.position = position;
    }

    public void set(String name, String color, String description, String links, UUID calendarAccountId) {
        this.name = name;
        this.color = color;
        this.description = description;
        this.links = links;
        this.calendarAccountId = calendarAccountId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public String getDescription() {
        return description;
    }

    public String getLinks() {
        return links;
    }

    public UUID getCalendarAccountId() {
        return calendarAccountId;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
