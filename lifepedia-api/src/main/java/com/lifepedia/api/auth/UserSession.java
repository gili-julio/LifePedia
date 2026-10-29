package com.lifepedia.api.auth;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Sessão de login; o JWT leva o id ({@code sid}) e só vale enquanto a linha existir e não vencer. */
@Entity
@Table(name = "user_sessions")
public class UserSession extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** "Lembrar de mim": cookie persistente e validade longa. */
    @Column(nullable = false, updatable = false)
    private boolean remember;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected UserSession() {
    }

    public UserSession(UUID userId, boolean remember, Instant expiresAt) {
        this.userId = userId;
        this.remember = remember;
        this.expiresAt = expiresAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isRemember() {
        return remember;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
