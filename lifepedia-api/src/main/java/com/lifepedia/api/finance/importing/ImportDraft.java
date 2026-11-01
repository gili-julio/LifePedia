package com.lifepedia.api.finance.importing;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** Leitura pela IA ou revisão de uma importação; {@code rows} é o estado da prévia, guardado como o front mandou. */
@Entity
@Table(name = "import_drafts")
public class ImportDraft extends BaseEntity {

    /** SITE = leitura pela IA da API, que conta nos limites de envio. Espelhado no front. */
    public enum Origin { SITE, MANUAL }

    /** Espelhado no front. */
    public enum Status { RUNNING, REVIEW, FAILED, CLOSED }

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Origin origin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "file_count", nullable = false, updatable = false)
    private int fileCount;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    /** JSON da IA, até a primeira gravação da prévia. */
    private String result;

    @Column(name = "rows_json")
    private String rows;

    private String error;

    protected ImportDraft() {
    }

    static ImportDraft reading(UUID userId, int fileCount) {
        ImportDraft draft = new ImportDraft();
        draft.userId = userId;
        draft.origin = Origin.SITE;
        draft.status = Status.RUNNING;
        draft.fileCount = fileCount;
        return draft;
    }

    static ImportDraft manual(UUID userId, String rows, int itemCount) {
        ImportDraft draft = new ImportDraft();
        draft.userId = userId;
        draft.origin = Origin.MANUAL;
        draft.status = Status.REVIEW;
        draft.rows = rows;
        draft.itemCount = itemCount;
        return draft;
    }

    void ready(String text, int items) {
        status = Status.REVIEW;
        result = text;
        itemCount = items;
    }

    void fail(String message) {
        status = Status.FAILED;
        error = message;
    }

    void updateRows(String json, int items) {
        rows = json;
        itemCount = items;
        result = null;
    }

    /** Fica só a data do envio, para os limites. */
    void close() {
        status = Status.CLOSED;
        result = null;
        rows = null;
        error = null;
    }

    public UUID getUserId() {
        return userId;
    }

    public Origin getOrigin() {
        return origin;
    }

    public Status getStatus() {
        return status;
    }

    public int getFileCount() {
        return fileCount;
    }

    public int getItemCount() {
        return itemCount;
    }

    public String getResult() {
        return result;
    }

    public String getRows() {
        return rows;
    }

    public String getError() {
        return error;
    }
}
