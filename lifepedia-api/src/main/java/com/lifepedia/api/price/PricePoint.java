package com.lifepedia.api.price;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Preço de uma loja num dia (um por loja, dia e origem). */
@Entity
@Table(name = "price_points")
public class PricePoint extends BaseEntity {

    public enum Source { BUSCAPE, LINK, MANUAL }

    @Column(name = "watch_id", nullable = false, updatable = false)
    private UUID watchId;

    @Column(nullable = false, updatable = false)
    private LocalDate day;

    @Column(nullable = false, length = 80, updatable = false)
    private String store;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Source source;

    @Column(length = 2048)
    private String url;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    protected PricePoint() {
    }

    public PricePoint(UUID watchId, LocalDate day, String store, Source source, UUID createdBy) {
        this.watchId = watchId;
        this.day = day;
        this.store = store;
        this.source = source;
        this.createdBy = createdBy;
    }

    public void setPrice(BigDecimal price, String url) {
        this.price = price;
        this.url = url;
    }

    public UUID getWatchId() {
        return watchId;
    }

    public LocalDate getDay() {
        return day;
    }

    public String getStore() {
        return store;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Source getSource() {
        return source;
    }

    public String getUrl() {
        return url;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
