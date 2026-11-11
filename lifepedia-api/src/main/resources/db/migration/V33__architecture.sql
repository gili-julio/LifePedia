-- Arquitetura: projetos com maquete 3D (GLB comprimido no navegador), versões, compartilhamento, comentários,
-- vistas salvas e anexos. Arquivos ficam em STORAGE_DIR/architecture; aqui só a chave.
CREATE TABLE arch_projects (
    id                 UUID PRIMARY KEY,
    user_id            UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name               VARCHAR(120)             NOT NULL,
    description        VARCHAR(1000),
    latitude           NUMERIC(8, 5),
    north_degrees      INTEGER                  NOT NULL DEFAULT 0,
    current_version_id UUID,
    cover_key          VARCHAR(32),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_arch_projects_user ON arch_projects (user_id, updated_at DESC);

-- PENDING = criada, esperando o arquivo (some sozinha depois de 2 h); number só ao ficar pronta.
CREATE TABLE arch_versions (
    id           UUID PRIMARY KEY,
    project_id   UUID                     NOT NULL REFERENCES arch_projects (id) ON DELETE CASCADE,
    number       INTEGER,
    label        VARCHAR(120),
    note         VARCHAR(1000),
    status       VARCHAR(10)              NOT NULL CHECK (status IN ('PENDING', 'READY')),
    file_key     VARCHAR(32),
    size_bytes   BIGINT                   NOT NULL,
    source_name  VARCHAR(255),
    source_bytes BIGINT,
    created_by   UUID                     REFERENCES users (id) ON DELETE SET NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (project_id, number)
);

CREATE INDEX idx_arch_versions_pending ON arch_versions (created_at) WHERE status = 'PENDING';

ALTER TABLE arch_projects ADD CONSTRAINT fk_arch_projects_current
    FOREIGN KEY (current_version_id) REFERENCES arch_versions (id) ON DELETE SET NULL;

-- Com uma pessoa (amiga do dono) ou com um grupo do dono; papel VIEW < COMMENT < EDIT.
CREATE TABLE arch_shares (
    id         UUID PRIMARY KEY,
    project_id UUID                     NOT NULL REFERENCES arch_projects (id) ON DELETE CASCADE,
    user_id    UUID REFERENCES users (id) ON DELETE CASCADE,
    group_id   UUID REFERENCES user_groups (id) ON DELETE CASCADE,
    role       VARCHAR(10)              NOT NULL CHECK (role IN ('VIEW', 'COMMENT', 'EDIT')),
    created_by UUID                     REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((user_id IS NULL) <> (group_id IS NULL)),
    UNIQUE (project_id, user_id),
    UNIQUE (project_id, group_id)
);

CREATE INDEX idx_arch_shares_user ON arch_shares (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_arch_shares_group ON arch_shares (group_id) WHERE group_id IS NOT NULL;

-- Link público (sem login): só a maquete da versão atual e as vistas, até expirar ou ser apagado.
CREATE TABLE arch_links (
    id         UUID PRIMARY KEY,
    project_id UUID                     NOT NULL REFERENCES arch_projects (id) ON DELETE CASCADE,
    token      VARCHAR(64)              NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID                     REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_arch_links_project ON arch_links (project_id);

-- Comentário preso a um ponto da maquete de uma versão.
CREATE TABLE arch_comments (
    id          UUID PRIMARY KEY,
    project_id  UUID                     NOT NULL REFERENCES arch_projects (id) ON DELETE CASCADE,
    version_id  UUID                     NOT NULL REFERENCES arch_versions (id) ON DELETE CASCADE,
    user_id     UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body        VARCHAR(1000)            NOT NULL,
    x           DOUBLE PRECISION         NOT NULL,
    y           DOUBLE PRECISION         NOT NULL,
    z           DOUBLE PRECISION         NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_arch_comments_version ON arch_comments (version_id, created_at);

-- Câmera salva com nome (posição e alvo, em metros).
CREATE TABLE arch_views (
    id         UUID PRIMARY KEY,
    project_id UUID                     NOT NULL REFERENCES arch_projects (id) ON DELETE CASCADE,
    user_id    UUID                     REFERENCES users (id) ON DELETE SET NULL,
    name       VARCHAR(60)              NOT NULL,
    px         DOUBLE PRECISION         NOT NULL,
    py         DOUBLE PRECISION         NOT NULL,
    pz         DOUBLE PRECISION         NOT NULL,
    tx         DOUBLE PRECISION         NOT NULL,
    ty         DOUBLE PRECISION         NOT NULL,
    tz         DOUBLE PRECISION         NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_arch_views_project ON arch_views (project_id, created_at);

-- Renders e fotos (JPEG regravado) e plantas em PDF; contam no espaço do dono do projeto.
CREATE TABLE arch_attachments (
    id         UUID PRIMARY KEY,
    project_id UUID                     NOT NULL REFERENCES arch_projects (id) ON DELETE CASCADE,
    user_id    UUID                     REFERENCES users (id) ON DELETE SET NULL,
    kind       VARCHAR(10)              NOT NULL CHECK (kind IN ('IMAGE', 'PDF')),
    name       VARCHAR(200)             NOT NULL,
    file_key   VARCHAR(32)              NOT NULL,
    size_bytes BIGINT                   NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_arch_attachments_project ON arch_attachments (project_id, created_at DESC);
