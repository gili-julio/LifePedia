-- Leitura de documentos pela IA e rascunho da revisão da importação. A linha fechada fica sem conteúdo, só para contar
-- os limites de envio.
CREATE TABLE import_drafts (
    id         UUID PRIMARY KEY,
    user_id    UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    origin     VARCHAR(10)              NOT NULL,
    status     VARCHAR(10)              NOT NULL,
    file_count INTEGER                  NOT NULL DEFAULT 0,
    item_count INTEGER                  NOT NULL DEFAULT 0,
    result     TEXT,
    rows_json  TEXT,
    error      VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Uma revisão por pessoa.
CREATE UNIQUE INDEX uq_import_drafts_open ON import_drafts (user_id) WHERE status IN ('RUNNING', 'REVIEW', 'FAILED');
CREATE INDEX idx_import_drafts_user_created ON import_drafts (user_id, created_at DESC);
