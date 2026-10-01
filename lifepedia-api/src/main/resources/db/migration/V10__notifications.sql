-- Notificações do usuário. "type" define o texto no front; "data" leva os parâmetros (nomes no momento do envio).
CREATE TABLE notifications (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(40) NOT NULL,
    actor_id   UUID        REFERENCES users (id) ON DELETE SET NULL,
    group_id   UUID        REFERENCES user_groups (id) ON DELETE CASCADE,
    data       JSONB       NOT NULL DEFAULT '{}',
    read_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;
