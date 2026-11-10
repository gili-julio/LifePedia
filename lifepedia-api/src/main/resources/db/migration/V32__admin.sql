-- Administração: papel de admin, bloqueio de conta e último acesso (atualizado no máximo a cada 10 min).
ALTER TABLE users ADD COLUMN admin BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN blocked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN last_seen_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_users_created ON users (created_at DESC);
CREATE INDEX idx_users_last_seen ON users (last_seen_at DESC);
CREATE INDEX idx_users_admin ON users (id) WHERE admin;

-- Registro das ações dos admins. Nomes guardados na época (a conta pode ser excluída depois).
CREATE TABLE admin_audit_log (
    id          UUID PRIMARY KEY,
    actor_id    UUID                     REFERENCES users (id) ON DELETE SET NULL,
    actor_name  VARCHAR(120)             NOT NULL,
    action      VARCHAR(30)              NOT NULL,
    target_id   UUID                     REFERENCES users (id) ON DELETE SET NULL,
    target_name VARCHAR(120),
    detail      VARCHAR(300),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_admin_audit_created ON admin_audit_log (created_at DESC);

-- Aviso no topo do app para todos; ended_at = encerrado antes da hora (ou ao vencer, ends_at).
CREATE TABLE announcements (
    id         UUID PRIMARY KEY,
    message    VARCHAR(500)             NOT NULL,
    level      VARCHAR(10)              NOT NULL CHECK (level IN ('INFO', 'WARNING')),
    ends_at    TIMESTAMP WITH TIME ZONE,
    ended_at   TIMESTAMP WITH TIME ZONE,
    created_by UUID                     REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_announcements_created ON announcements (created_at DESC);

-- Uso da IA (só o resultado, sem documento nem resposta), para o painel e o limite diário.
CREATE TABLE ai_usage (
    id          UUID PRIMARY KEY,
    user_id     UUID                     REFERENCES users (id) ON DELETE SET NULL,
    feature     VARCHAR(20)              NOT NULL,
    outcome     VARCHAR(20)              NOT NULL,
    file_count  INTEGER                  NOT NULL DEFAULT 0,
    item_count  INTEGER                  NOT NULL DEFAULT 0,
    duration_ms BIGINT                   NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_ai_usage_created ON ai_usage (created_at DESC);
CREATE INDEX idx_ai_usage_user ON ai_usage (user_id);
