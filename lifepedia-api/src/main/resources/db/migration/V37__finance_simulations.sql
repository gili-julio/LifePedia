-- Simulações salvas do simulador do financeiro (pessoais); input = pedido em JSON, refeito com os dados do dia ao abrir.
CREATE TABLE finance_simulations (
    id         UUID PRIMARY KEY,
    user_id    UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(120)             NOT NULL,
    input      TEXT                     NOT NULL CHECK (length(input) <= 20000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_finance_simulations_user ON finance_simulations (user_id, created_at DESC);
