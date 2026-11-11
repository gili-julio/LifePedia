-- Cena de cada versão da maquete: variantes de acabamento, cômodos marcados e luzes da noite.

-- Variante: troca de cor/textura por material do GLB (índice do material → ajuste), em JSON.
CREATE TABLE arch_variants (
    id         UUID PRIMARY KEY,
    project_id UUID                     NOT NULL REFERENCES arch_projects (id) ON DELETE CASCADE,
    version_id UUID                     NOT NULL REFERENCES arch_versions (id) ON DELETE CASCADE,
    user_id    UUID                     REFERENCES users (id) ON DELETE SET NULL,
    name       VARCHAR(60)              NOT NULL,
    materials  TEXT                     NOT NULL CHECK (length(materials) <= 60000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_arch_variants_version ON arch_variants (version_id, created_at);

-- Cômodo: ponto no piso, área aproximada e contorno (pares x/z em metros, em JSON) achados no navegador.
CREATE TABLE arch_rooms (
    id         UUID PRIMARY KEY,
    project_id UUID                     NOT NULL REFERENCES arch_projects (id) ON DELETE CASCADE,
    version_id UUID                     NOT NULL REFERENCES arch_versions (id) ON DELETE CASCADE,
    user_id    UUID                     REFERENCES users (id) ON DELETE SET NULL,
    name       VARCHAR(60)              NOT NULL,
    x          DOUBLE PRECISION         NOT NULL,
    y          DOUBLE PRECISION         NOT NULL,
    z          DOUBLE PRECISION         NOT NULL,
    ceiling    DOUBLE PRECISION,
    area       NUMERIC(10, 2),
    outline    TEXT                     NOT NULL CHECK (length(outline) <= 10000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_arch_rooms_version ON arch_rooms (version_id, created_at);

CREATE TABLE arch_lights (
    id         UUID PRIMARY KEY,
    project_id UUID                     NOT NULL REFERENCES arch_projects (id) ON DELETE CASCADE,
    version_id UUID                     NOT NULL REFERENCES arch_versions (id) ON DELETE CASCADE,
    user_id    UUID                     REFERENCES users (id) ON DELETE SET NULL,
    x          DOUBLE PRECISION         NOT NULL,
    y          DOUBLE PRECISION         NOT NULL,
    z          DOUBLE PRECISION         NOT NULL,
    color      VARCHAR(7)               NOT NULL,
    intensity  DOUBLE PRECISION         NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_arch_lights_version ON arch_lights (version_id, created_at);
