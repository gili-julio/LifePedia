package com.lifepedia.api.planning;

import com.lifepedia.api.finance.YearMonthConverter;
import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/** Algo que o grupo quer comprar ou investir até {@code targetMonth}. */
@Entity
@Table(name = "plan_items")
public class PlanItem extends BaseEntity {

    public enum Kind { PURCHASE, INVESTMENT }

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    /** Quem cadastrou. */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Kind kind;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Convert(converter = YearMonthConverter.class)
    @Column(name = "target_month", nullable = false)
    private YearMonth targetMonth;

    @Column(nullable = false)
    private int installments = 1;

    /** Prioridade: menor recebe o dinheiro primeiro. */
    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 10000)
    private String notes;

    @Column(length = 2048)
    private String url;

    @Column(name = "link_title", length = 300)
    private String linkTitle;

    @Column(name = "link_site", length = 120)
    private String linkSite;

    @Column(name = "link_price", precision = 14, scale = 2)
    private BigDecimal linkPrice;

    @Column(name = "reference_site", length = 60)
    private String referenceSite;

    @Column(name = "reference_title", length = 300)
    private String referenceTitle;

    @Column(name = "reference_url", length = 2048)
    private String referenceUrl;

    @Column(name = "reference_price", precision = 14, scale = 2)
    private BigDecimal referencePrice;

    @Column(name = "image_key", length = 32)
    private String imageKey;

    @Column(name = "acquired_on")
    private LocalDate acquiredOn;

    @Column(name = "acquired_amount", precision = 14, scale = 2)
    private BigDecimal acquiredAmount;

    protected PlanItem() {
    }

    public PlanItem(UUID groupId, UUID userId, int position) {
        this.groupId = groupId;
        this.userId = userId;
        this.position = position;
    }

    public void set(String name, Kind kind, BigDecimal amount, YearMonth targetMonth, int installments, String notes) {
        this.name = name;
        this.kind = kind;
        this.amount = amount;
        this.targetMonth = targetMonth;
        this.installments = installments;
        this.notes = notes;
    }

    public void setLink(String url, String title, String site, BigDecimal price) {
        this.url = url;
        this.linkTitle = title;
        this.linkSite = site;
        this.linkPrice = price;
    }

    /** Produto parecido de outro site (preço/foto de referência); {@code site} nulo limpa. */
    public void setReference(String site, String title, String url, BigDecimal price) {
        this.referenceSite = site;
        this.referenceTitle = site == null ? null : title;
        this.referenceUrl = site == null ? null : url;
        this.referencePrice = site == null ? null : price;
    }

    /** Marca como comprado/feito ({@code on} nulo desfaz). */
    public void acquire(LocalDate on, BigDecimal amount) {
        this.acquiredOn = on;
        this.acquiredAmount = amount;
    }

    public boolean isAcquired() {
        return acquiredOn != null;
    }

    /** URL da imagem (com versão para cache); nula sem imagem. */
    public String imageUrl() {
        return imageKey == null ? null
                : "/api/groups/" + groupId + "/planning/items/" + getId() + "/image?v=" + imageKey;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public Kind getKind() {
        return kind;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public YearMonth getTargetMonth() {
        return targetMonth;
    }

    public int getInstallments() {
        return installments;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getNotes() {
        return notes;
    }

    public String getUrl() {
        return url;
    }

    public String getLinkTitle() {
        return linkTitle;
    }

    public String getLinkSite() {
        return linkSite;
    }

    public BigDecimal getLinkPrice() {
        return linkPrice;
    }

    public String getReferenceSite() {
        return referenceSite;
    }

    public String getReferenceTitle() {
        return referenceTitle;
    }

    public String getReferenceUrl() {
        return referenceUrl;
    }

    public BigDecimal getReferencePrice() {
        return referencePrice;
    }

    public String getImageKey() {
        return imageKey;
    }

    public void setImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    public LocalDate getAcquiredOn() {
        return acquiredOn;
    }

    public BigDecimal getAcquiredAmount() {
        return acquiredAmount;
    }
}
