-- Raias acompanhadas por repositório (etiquetas em ordem, com limite de dias opcional) e há quanto tempo a issue está nela.
ALTER TABLE dev_repos ADD COLUMN lanes TEXT CHECK (length(lanes) <= 2000);

ALTER TABLE dev_items ADD COLUMN lane VARCHAR(100);
ALTER TABLE dev_items ADD COLUMN lane_since TIMESTAMP WITH TIME ZONE;
ALTER TABLE dev_items ADD COLUMN lane_warned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE dev_items ADD COLUMN opened_at TIMESTAMP WITH TIME ZONE;

-- O que a pessoa fez em cada dia num projeto.
CREATE TABLE dev_reports (
    id         UUID PRIMARY KEY,
    project_id UUID                     NOT NULL REFERENCES dev_projects (id) ON DELETE CASCADE,
    user_id    UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day        DATE                     NOT NULL,
    text       TEXT                     NOT NULL CHECK (length(text) <= 4000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (project_id, day)
);

CREATE INDEX idx_dev_reports_user ON dev_reports (user_id, day);
