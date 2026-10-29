-- Notificações push (Web Push): uma linha por aparelho/navegador. Só recebe enquanto a sessão de login existir
-- (logout ou sessão vencida deixam session_id nulo; o mesmo usuário reativa ao entrar de novo).
CREATE TABLE push_subscriptions (
    id         UUID PRIMARY KEY,
    user_id    UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    session_id UUID                     REFERENCES user_sessions (id) ON DELETE SET NULL,
    endpoint   VARCHAR(1000)            NOT NULL,
    p256dh     VARCHAR(100)             NOT NULL,
    auth       VARCHAR(50)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_push_subscriptions_endpoint UNIQUE (endpoint)
);

CREATE INDEX idx_push_subscriptions_user ON push_subscriptions (user_id);
CREATE INDEX idx_push_subscriptions_session ON push_subscriptions (session_id);
