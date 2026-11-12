package com.lifepedia.api.integration;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Conta externa ligada pela pessoa; tokens sempre cifrados pelo {@link SecretBox}. */
@Entity
@Table(name = "integration_accounts")
public class IntegrationAccount extends BaseEntity {

    /** {@code ERROR} = o serviço recusou o token (precisa reconectar); falha passageira não muda o status. */
    public enum Status { ACTIVE, ERROR }

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private IntegrationProvider provider;

    @Column(nullable = false, length = 40)
    private String nickname;

    @Column(nullable = false, length = 7)
    private String color;

    @Column(name = "base_url", length = 200, updatable = false)
    private String baseUrl;

    @Column(name = "external_id", nullable = false, length = 100, updatable = false)
    private String externalId;

    @Column(nullable = false, length = 200)
    private String login;

    @Column(name = "access_token")
    private String accessToken;

    @Column(name = "refresh_token")
    private String refreshToken;

    @Column(name = "access_expires_at")
    private OffsetDateTime accessExpiresAt;

    @Column(name = "token_expires_at")
    private LocalDate tokenExpiresAt;

    @Column(name = "can_write", nullable = false)
    private boolean canWrite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.ACTIVE;

    @Column(name = "last_sync_at")
    private OffsetDateTime lastSyncAt;

    @Column(name = "last_error", length = 300)
    private String lastError;

    @Column(name = "expiry_warned_on")
    private LocalDate expiryWarnedOn;

    @Column(length = 4000)
    private String stats;

    protected IntegrationAccount() {
    }

    public IntegrationAccount(UUID userId, IntegrationProvider provider, String baseUrl, String externalId) {
        this.userId = userId;
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.externalId = externalId;
    }

    public void setLabel(String nickname, String color) {
        this.nickname = nickname;
        this.color = color;
    }

    /** Token novo (conectar ou reconectar): volta a ficar ativa e o aviso de validade pode sair de novo. */
    public void setCredentials(String login, String accessToken, String refreshToken, LocalDate tokenExpiresAt, boolean canWrite) {
        this.login = login;
        this.accessToken = accessToken;
        if (refreshToken != null) this.refreshToken = refreshToken;
        this.tokenExpiresAt = tokenExpiresAt;
        this.canWrite = canWrite;
        this.status = Status.ACTIVE;
        this.lastError = null;
        this.expiryWarnedOn = null;
    }

    public void setAccess(String accessToken, OffsetDateTime accessExpiresAt) {
        this.accessToken = accessToken;
        this.accessExpiresAt = accessExpiresAt;
    }

    public void synced(OffsetDateTime at) {
        this.lastSyncAt = at;
        this.lastError = null;
        this.status = Status.ACTIVE;
    }

    /** Falha da sincronização; {@code revoked} leva a conta para {@link Status#ERROR}. */
    public void failed(OffsetDateTime at, String message, boolean revoked) {
        this.lastSyncAt = at;
        this.lastError = message.length() > 300 ? message.substring(0, 300) : message;
        if (revoked) this.status = Status.ERROR;
    }

    public UUID getUserId() {
        return userId;
    }

    public IntegrationProvider getProvider() {
        return provider;
    }

    public String getNickname() {
        return nickname;
    }

    public String getColor() {
        return color;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getLogin() {
        return login;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public OffsetDateTime getAccessExpiresAt() {
        return accessExpiresAt;
    }

    public LocalDate getTokenExpiresAt() {
        return tokenExpiresAt;
    }

    public void setTokenExpiresAt(LocalDate tokenExpiresAt) {
        this.tokenExpiresAt = tokenExpiresAt;
    }

    public boolean isCanWrite() {
        return canWrite;
    }

    public Status getStatus() {
        return status;
    }

    public OffsetDateTime getLastSyncAt() {
        return lastSyncAt;
    }

    public String getLastError() {
        return lastError;
    }

    public LocalDate getExpiryWarnedOn() {
        return expiryWarnedOn;
    }

    public void setExpiryWarnedOn(LocalDate expiryWarnedOn) {
        this.expiryWarnedOn = expiryWarnedOn;
    }

    public String getStats() {
        return stats;
    }

    public void setStats(String stats) {
        this.stats = stats;
    }
}
