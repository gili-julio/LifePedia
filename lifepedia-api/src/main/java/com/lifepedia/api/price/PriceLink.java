package com.lifepedia.api.price;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Link de loja colado para um item; lido a cada consulta. */
@Entity
@Table(name = "price_links")
public class PriceLink extends BaseEntity {

    @Column(name = "watch_id", nullable = false, updatable = false)
    private UUID watchId;

    @Column(nullable = false, length = 2048, updatable = false)
    private String url;

    @Column(length = 80)
    private String store;

    @Column(name = "last_price", precision = 14, scale = 2)
    private BigDecimal lastPrice;

    @Column(name = "last_checked_at")
    private OffsetDateTime lastCheckedAt;

    @Column(name = "last_error", length = 200)
    private String lastError;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    protected PriceLink() {
    }

    public PriceLink(UUID watchId, String url, String store, UUID createdBy) {
        this.watchId = watchId;
        this.url = url;
        this.store = store;
        this.createdBy = createdBy;
    }

    public void checked(String store, BigDecimal price, String error, OffsetDateTime now) {
        if (store != null) this.store = store;
        if (price != null) this.lastPrice = price;
        this.lastError = error;
        this.lastCheckedAt = now;
    }

    public UUID getWatchId() {
        return watchId;
    }

    public String getUrl() {
        return url;
    }

    public String getStore() {
        return store;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public OffsetDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public String getLastError() {
        return lastError;
    }
}
