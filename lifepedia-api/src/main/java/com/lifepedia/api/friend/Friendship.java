package com.lifepedia.api.friend;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Pedido de amizade ({@code PENDING}) ou amizade ({@code ACCEPTED}); recusar ou desfazer apaga a linha. */
@Entity
@Table(name = "friendships")
public class Friendship extends BaseEntity {

    public enum Status { PENDING, ACCEPTED }

    @Column(name = "requester_id", nullable = false, updatable = false)
    private UUID requesterId;

    @Column(name = "addressee_id", nullable = false, updatable = false)
    private UUID addresseeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.PENDING;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    protected Friendship() {
    }

    public Friendship(UUID requesterId, UUID addresseeId) {
        this.requesterId = requesterId;
        this.addresseeId = addresseeId;
    }

    public void accept() {
        status = Status.ACCEPTED;
        acceptedAt = OffsetDateTime.now();
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public boolean involves(UUID userId) {
        return requesterId.equals(userId) || addresseeId.equals(userId);
    }

    /** A outra pessoa da amizade. */
    public UUID other(UUID userId) {
        return requesterId.equals(userId) ? addresseeId : requesterId;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public UUID getAddresseeId() {
        return addresseeId;
    }

    public Status getStatus() {
        return status;
    }

    public OffsetDateTime getAcceptedAt() {
        return acceptedAt;
    }
}
