package com.lifepedia.api.price;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Item com preço acompanhado: pessoal (sem grupo) ou do grupo, opcionalmente ligado a um item do planejamento. */
@Entity
@Table(name = "price_watches")
public class PriceWatch extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "group_id", updatable = false)
    private UUID groupId;

    @Column(name = "plan_item_id")
    private UUID planItemId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "buscape_url", length = 500)
    private String buscapeUrl;

    @Column(name = "image_key", length = 80)
    private String imageKey;

    @Column(name = "target_price", precision = 14, scale = 2)
    private BigDecimal targetPrice;

    @Column(name = "drop_percent", nullable = false)
    private int dropPercent = 10;

    @Column(name = "notify_group", nullable = false)
    private boolean notifyGroup;

    @Column(name = "update_plan", nullable = false)
    private boolean updatePlan;

    @Column(nullable = false)
    private boolean active = true;

    /** Ofertas da última consulta, em JSON ({@link PriceDtos.Offer}). */
    @Column(length = 60000)
    private String offers;

    @Column(name = "best_price", precision = 14, scale = 2)
    private BigDecimal bestPrice;

    @Column(name = "best_store", length = 80)
    private String bestStore;

    @Column(name = "best_url", length = 2048)
    private String bestUrl;

    @Column(name = "store_count")
    private Integer storeCount;

    /** Preço do último aviso (só avisa de novo se baixar mais). */
    @Column(name = "alerted_price", precision = 14, scale = 2)
    private BigDecimal alertedPrice;

    @Column(name = "last_checked_at")
    private OffsetDateTime lastCheckedAt;

    @Column(name = "last_error", length = 200)
    private String lastError;

    protected PriceWatch() {
    }

    public PriceWatch(UUID userId, UUID groupId) {
        this.userId = userId;
        this.groupId = groupId;
    }

    public void set(String name, BigDecimal targetPrice, int dropPercent, boolean notifyGroup, boolean updatePlan,
                    boolean active) {
        this.name = name;
        this.targetPrice = targetPrice;
        this.dropPercent = dropPercent;
        this.notifyGroup = notifyGroup;
        this.updatePlan = updatePlan;
        this.active = active;
    }

    /** Ofertas atuais em JSON e o menor preço. */
    public void setOffers(String offers, BigDecimal bestPrice, String bestStore, String bestUrl, Integer storeCount) {
        this.offers = offers;
        this.bestPrice = bestPrice;
        this.bestStore = bestStore;
        this.bestUrl = bestUrl;
        this.storeCount = storeCount;
    }

    public void checked(String error, OffsetDateTime now) {
        this.lastError = error;
        this.lastCheckedAt = now;
    }

    public boolean isPersonal() {
        return groupId == null;
    }

    /** URL da foto (com versão para cache); nula sem foto. */
    public String imageUrl() {
        return imageKey == null ? null : "/api/prices/" + getId() + "/image?v=" + imageKey;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getPlanItemId() {
        return planItemId;
    }

    public void setPlanItemId(UUID planItemId) {
        this.planItemId = planItemId;
    }

    public String getName() {
        return name;
    }

    public String getBuscapeUrl() {
        return buscapeUrl;
    }

    public void setBuscapeUrl(String buscapeUrl) {
        this.buscapeUrl = buscapeUrl;
    }

    public String getImageKey() {
        return imageKey;
    }

    public void setImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    public BigDecimal getTargetPrice() {
        return targetPrice;
    }

    public int getDropPercent() {
        return dropPercent;
    }

    public boolean isNotifyGroup() {
        return notifyGroup;
    }

    public boolean isUpdatePlan() {
        return updatePlan;
    }

    public boolean isActive() {
        return active;
    }

    public String getOffers() {
        return offers;
    }

    public BigDecimal getBestPrice() {
        return bestPrice;
    }

    public String getBestStore() {
        return bestStore;
    }

    public String getBestUrl() {
        return bestUrl;
    }

    public Integer getStoreCount() {
        return storeCount;
    }

    public BigDecimal getAlertedPrice() {
        return alertedPrice;
    }

    public void setAlertedPrice(BigDecimal alertedPrice) {
        this.alertedPrice = alertedPrice;
    }

    public OffsetDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public String getLastError() {
        return lastError;
    }
}
