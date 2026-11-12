package com.lifepedia.api.dev;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Repositório do GitLab/GitHub num projeto, com as regras de raia (etiquetas da issue ao abrir e aceitar MR). */
@Entity
@Table(name = "dev_repos")
public class DevRepo extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "external_id", nullable = false, length = 200, updatable = false)
    private String externalId;

    @Column(name = "full_name", nullable = false, length = 300)
    private String fullName;

    @Column(name = "web_url", nullable = false, length = 500)
    private String webUrl;

    @Column(name = "default_branch", length = 200)
    private String defaultBranch;

    @Column(name = "import_issues", nullable = false)
    private boolean importIssues = true;

    @Column(name = "close_issues", nullable = false)
    private boolean closeIssues;

    @Column(name = "label_mr_opened", length = 100)
    private String labelMrOpened;

    @Column(name = "label_mr_merged", length = 100)
    private String labelMrMerged;

    @Column(name = "labels_clear", length = 400)
    private String labelsClear;

    /** Só MR aberto ou aceito depois disso move issue (regra nova não mexe no passado). */
    @Column(name = "rules_since", nullable = false)
    private OffsetDateTime rulesSince;

    /** Última sincronização; nula = a primeira ainda não rodou (nela não sai aviso). */
    @Column(name = "synced_at")
    private OffsetDateTime syncedAt;

    protected DevRepo() {
    }

    public DevRepo(UUID projectId, UUID userId, UUID accountId, String externalId, OffsetDateTime rulesSince) {
        this.projectId = projectId;
        this.userId = userId;
        this.accountId = accountId;
        this.externalId = externalId;
        this.rulesSince = rulesSince;
    }

    public void setRemote(String fullName, String webUrl, String defaultBranch) {
        this.fullName = fullName;
        this.webUrl = webUrl;
        this.defaultBranch = defaultBranch;
    }

    /** Regras novas; se alguma etiqueta mudou, só valem para MRs a partir de {@code now}. */
    public void setRules(boolean importIssues, boolean closeIssues, String opened, String merged, String clear, OffsetDateTime now) {
        boolean changed = !Objects.equals(labelMrOpened, opened) || !Objects.equals(labelMrMerged, merged)
                || !Objects.equals(labelsClear, clear);
        this.importIssues = importIssues;
        this.closeIssues = closeIssues;
        this.labelMrOpened = opened;
        this.labelMrMerged = merged;
        this.labelsClear = clear;
        if (changed) this.rulesSince = now;
    }

    /** Etiquetas que saem quando a issue muda de raia. */
    public List<String> clearList() {
        if (labelsClear == null || labelsClear.isBlank()) return List.of();
        return Arrays.stream(labelsClear.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getWebUrl() {
        return webUrl;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public boolean isImportIssues() {
        return importIssues;
    }

    public boolean isCloseIssues() {
        return closeIssues;
    }

    public String getLabelMrOpened() {
        return labelMrOpened;
    }

    public String getLabelMrMerged() {
        return labelMrMerged;
    }

    public String getLabelsClear() {
        return labelsClear;
    }

    public OffsetDateTime getRulesSince() {
        return rulesSince;
    }

    public OffsetDateTime getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(OffsetDateTime syncedAt) {
        this.syncedAt = syncedAt;
    }
}
