package com.lifepedia.api.notification.push;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Aparelho que recebe push; {@code sessionId} nulo = sessão encerrada, não recebe até o dono entrar de novo. */
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(nullable = false, length = 1000, updatable = false)
    private String endpoint;

    @Column(nullable = false, length = 100)
    private String p256dh;

    @Column(nullable = false, length = 50)
    private String auth;

    protected PushSubscription() {
    }

    public PushSubscription(UUID userId, UUID sessionId, String endpoint, String p256dh, String auth) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    /** Liga o aparelho a outra sessão (ou a outra pessoa, quando ela ativa neste navegador). */
    public void attach(UUID userId, UUID sessionId, String p256dh, String auth) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuth() {
        return auth;
    }
}
