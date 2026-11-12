package com.lifepedia.api.dev;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** MR, issue ou pipeline como veio na última sincronização (retrato, refeito a cada vez). */
@Entity
@Table(name = "dev_items")
public class DevItem extends BaseEntity {

    public enum Kind { MR, ISSUE, PIPELINE }

    @Column(name = "repo_id", nullable = false, updatable = false)
    private UUID repoId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Kind kind;

    @Column(name = "external_id", nullable = false, length = 100, updatable = false)
    private String externalId;

    @Column(length = 20)
    private String number;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 20)
    private String state;

    @Column(name = "web_url", nullable = false, length = 500)
    private String webUrl;

    @Column(length = 100)
    private String author;

    @Column(nullable = false)
    private boolean mine;

    @Column(nullable = false)
    private boolean review;

    @Column(nullable = false)
    private boolean draft;

    @Column(length = 20)
    private String pipeline;

    @Column(length = 200)
    private String branch;

    @Column(length = 1000)
    private String labels;

    @Column(nullable = false)
    private int comments;

    @Column(nullable = false)
    private boolean approved;

    @Column(name = "linked_issues", length = 500)
    private String linkedIssues;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "task_id")
    private UUID taskId;

    /** Já ganhou tarefa alguma vez (tarefa apagada não volta). */
    @Column(name = "task_linked", nullable = false)
    private boolean taskLinked;

    @Column(name = "remote_updated_at")
    private OffsetDateTime remoteUpdatedAt;

    @Column(name = "merged_at")
    private OffsetDateTime mergedAt;

    protected DevItem() {
    }

    public DevItem(UUID repoId, UUID userId, Kind kind, String externalId) {
        this.repoId = repoId;
        this.userId = userId;
        this.kind = kind;
        this.externalId = externalId;
    }

    public void setBasics(String number, String title, String state, String webUrl, String author, OffsetDateTime remoteUpdatedAt) {
        this.number = number;
        this.title = title == null || title.isBlank() ? "(sem título)" : title;
        this.state = state == null ? "" : state;
        this.webUrl = webUrl == null ? "" : webUrl;
        this.author = author;
        this.remoteUpdatedAt = remoteUpdatedAt;
    }

    public void setMergeRequest(boolean mine, boolean review, boolean draft, String branch, String pipeline, boolean approved,
                                int comments, String linkedIssues, OffsetDateTime mergedAt) {
        this.mine = mine;
        this.review = review;
        this.draft = draft;
        this.branch = branch;
        this.pipeline = pipeline;
        this.approved = approved;
        this.comments = comments;
        this.linkedIssues = linkedIssues;
        this.mergedAt = mergedAt;
    }

    public void setIssue(List<String> labels, LocalDate dueDate) {
        String joined = String.join(",", labels);
        this.labels = joined.length() <= 1000 ? joined : joined.substring(0, 1000);
        this.dueDate = dueDate;
    }

    public void setPipeline(String pipeline, String branch) {
        this.pipeline = pipeline;
        this.branch = branch;
    }

    public void linkTask(UUID taskId) {
        this.taskId = taskId;
        this.taskLinked = true;
    }

    public List<String> labelList() {
        return labels == null || labels.isBlank() ? List.of() : Arrays.asList(labels.split(","));
    }

    public UUID getRepoId() {
        return repoId;
    }

    public Kind getKind() {
        return kind;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public String getState() {
        return state;
    }

    public String getWebUrl() {
        return webUrl;
    }

    public String getAuthor() {
        return author;
    }

    public boolean isMine() {
        return mine;
    }

    public boolean isReview() {
        return review;
    }

    public boolean isDraft() {
        return draft;
    }

    public String getPipeline() {
        return pipeline;
    }

    public String getBranch() {
        return branch;
    }

    public int getComments() {
        return comments;
    }

    public boolean isApproved() {
        return approved;
    }

    public String getLinkedIssues() {
        return linkedIssues;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public boolean isTaskLinked() {
        return taskLinked;
    }

    public OffsetDateTime getRemoteUpdatedAt() {
        return remoteUpdatedAt;
    }

    public OffsetDateTime getMergedAt() {
        return mergedAt;
    }
}
