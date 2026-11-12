package com.lifepedia.api.delivery;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Entrega rastreada; o retrato do rastreio vem do 17TRACK ({@link TrackSnapshot}). */
@Entity
@Table(name = "deliveries")
public class Delivery extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "address_id")
    private UUID addressId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "tracking_code", nullable = false, length = 40, updatable = false)
    private String trackingCode;

    @Column(name = "image_key", length = 80)
    private String imageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(name = "sub_status", length = 60)
    private String subStatus;

    @Column(name = "tax_pending", nullable = false)
    private boolean taxPending;

    @Column(name = "carrier_key")
    private Integer carrierKey;

    @Column(name = "carrier_name", length = 80)
    private String carrierName;

    @Column(name = "carrier_url", length = 300)
    private String carrierUrl;

    @Column(name = "latest_at")
    private OffsetDateTime latestAt;

    @Column(name = "latest_text", length = 400)
    private String latestText;

    @Column(name = "latest_location", length = 160)
    private String latestLocation;

    @Column(length = 160)
    private String origin;

    @Column(name = "estimated_from")
    private LocalDate estimatedFrom;

    @Column(name = "estimated_to")
    private LocalDate estimatedTo;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(length = 60000)
    private String events;

    @Column(nullable = false)
    private boolean registered;

    @Column(name = "last_sync_at")
    private OffsetDateTime lastSyncAt;

    @Column(name = "sync_error", length = 200)
    private String syncError;

    @Enumerated(EnumType.STRING)
    @Column(name = "notified_status", length = 20)
    private DeliveryStatus notifiedStatus;

    @Column(name = "stall_warned", nullable = false)
    private boolean stallWarned;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    protected Delivery() {
    }

    public Delivery(UUID userId, String trackingCode) {
        this.userId = userId;
        this.trackingCode = trackingCode;
    }

    public void set(String name, UUID addressId, UUID groupId) {
        this.name = name;
        this.addressId = addressId;
        this.groupId = groupId;
    }

    /** Novo retrato do rastreio; {@code events} em JSON. */
    public void apply(TrackSnapshot s, String events, OffsetDateTime now) {
        if (s.latestAt() != null && !s.latestAt().equals(latestAt)) stallWarned = false;
        this.status = s.status();
        this.subStatus = s.subStatus();
        this.taxPending = s.taxPending();
        this.carrierKey = s.carrierKey();
        this.carrierName = s.carrierName();
        this.carrierUrl = s.carrierUrl();
        this.latestAt = s.latestAt();
        this.latestText = s.latestText();
        this.latestLocation = s.latestLocation();
        this.origin = s.origin();
        this.estimatedFrom = s.estimatedFrom();
        this.estimatedTo = s.estimatedTo();
        this.deliveredAt = s.status() == DeliveryStatus.DELIVERED ? (s.latestAt() != null ? s.latestAt() : now) : null;
        this.events = events;
        this.lastSyncAt = now;
        this.syncError = null;
    }

    public void failSync(String error, OffsetDateTime now) {
        this.lastSyncAt = now;
        this.syncError = error;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public UUID getAddressId() {
        return addressId;
    }

    public String getName() {
        return name;
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public String getImageKey() {
        return imageKey;
    }

    public void setImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    /** URL da foto (com versão para cache); nula sem foto. */
    public String imageUrl() {
        return imageKey == null ? null : "/api/deliveries/" + getId() + "/image?v=" + imageKey;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public String getSubStatus() {
        return subStatus;
    }

    public boolean isTaxPending() {
        return taxPending;
    }

    public Integer getCarrierKey() {
        return carrierKey;
    }

    public String getCarrierName() {
        return carrierName;
    }

    public String getCarrierUrl() {
        return carrierUrl;
    }

    public OffsetDateTime getLatestAt() {
        return latestAt;
    }

    public String getLatestText() {
        return latestText;
    }

    public String getLatestLocation() {
        return latestLocation;
    }

    public String getOrigin() {
        return origin;
    }

    public LocalDate getEstimatedFrom() {
        return estimatedFrom;
    }

    public LocalDate getEstimatedTo() {
        return estimatedTo;
    }

    public OffsetDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public String getEvents() {
        return events;
    }

    public boolean isRegistered() {
        return registered;
    }

    public void setRegistered(boolean registered) {
        this.registered = registered;
    }

    public OffsetDateTime getLastSyncAt() {
        return lastSyncAt;
    }

    public String getSyncError() {
        return syncError;
    }

    public DeliveryStatus getNotifiedStatus() {
        return notifiedStatus;
    }

    public void setNotifiedStatus(DeliveryStatus notifiedStatus) {
        this.notifiedStatus = notifiedStatus;
    }

    public boolean isStallWarned() {
        return stallWarned;
    }

    public void setStallWarned(boolean stallWarned) {
        this.stallWarned = stallWarned;
    }

    public OffsetDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(OffsetDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }
}
