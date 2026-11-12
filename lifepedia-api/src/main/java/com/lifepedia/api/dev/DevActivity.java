package com.lifepedia.api.dev;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/** Atividade da pessoa num repositório num dia (commits, MRs abertos e aceitos, revisões). */
@Entity
@Table(name = "dev_activity")
public class DevActivity extends BaseEntity {

    @Column(name = "repo_id", nullable = false, updatable = false)
    private UUID repoId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private LocalDate day;

    @Column(nullable = false)
    private int commits;

    @Column(name = "merge_requests", nullable = false)
    private int mergeRequests;

    @Column(nullable = false)
    private int merged;

    @Column(nullable = false)
    private int reviews;

    protected DevActivity() {
    }

    public DevActivity(UUID repoId, UUID userId, LocalDate day) {
        this.repoId = repoId;
        this.userId = userId;
        this.day = day;
    }

    public void set(int commits, int mergeRequests, int merged, int reviews) {
        this.commits = commits;
        this.mergeRequests = mergeRequests;
        this.merged = merged;
        this.reviews = reviews;
    }

    public UUID getRepoId() {
        return repoId;
    }

    public LocalDate getDay() {
        return day;
    }

    public int getCommits() {
        return commits;
    }

    public int getMergeRequests() {
        return mergeRequests;
    }

    public int getMerged() {
        return merged;
    }

    public int getReviews() {
        return reviews;
    }
}
