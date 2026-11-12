-- Contas externas ligadas (Google Agenda, GitLab, GitHub, Duolingo) e o módulo Dev.
-- Tokens ficam cifrados pela API (AES-GCM com INTEGRATIONS_KEY); o banco nunca vê o token puro.

CREATE TABLE integration_accounts (
    id                UUID PRIMARY KEY,
    user_id           UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider          VARCHAR(10)              NOT NULL,
    nickname          VARCHAR(40)              NOT NULL,
    color             VARCHAR(7)               NOT NULL,
    base_url          VARCHAR(200),
    external_id       VARCHAR(100)             NOT NULL,
    login             VARCHAR(200)             NOT NULL,
    access_token      TEXT,
    refresh_token     TEXT,
    access_expires_at TIMESTAMP WITH TIME ZONE,
    token_expires_at  DATE,
    can_write         BOOLEAN                  NOT NULL DEFAULT FALSE,
    status            VARCHAR(10)              NOT NULL,
    last_sync_at      TIMESTAMP WITH TIME ZONE,
    last_error        VARCHAR(300),
    expiry_warned_on  DATE,
    stats             TEXT CHECK (length(stats) <= 4000),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uq_integration_accounts ON integration_accounts (user_id, provider, coalesce(base_url, ''), external_id);
CREATE INDEX idx_integration_accounts_sync ON integration_accounts (status, last_sync_at);

-- Histórico das sincronizações (as 20 últimas de cada conta).
CREATE TABLE integration_syncs (
    id          UUID PRIMARY KEY,
    account_id  UUID                     NOT NULL REFERENCES integration_accounts (id) ON DELETE CASCADE,
    outcome     VARCHAR(10)              NOT NULL,
    manual      BOOLEAN                  NOT NULL,
    items       INT                      NOT NULL,
    message     VARCHAR(300),
    duration_ms BIGINT                   NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_integration_syncs_account ON integration_syncs (account_id, created_at DESC);

-- Eventos do Google Agenda (de 7 dias atrás a 60 à frente), refeitos a cada sincronização.
CREATE TABLE calendar_events (
    id          UUID PRIMARY KEY,
    account_id  UUID                     NOT NULL REFERENCES integration_accounts (id) ON DELETE CASCADE,
    user_id     UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    external_id VARCHAR(300)             NOT NULL,
    title       VARCHAR(300)             NOT NULL,
    starts_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    all_day     BOOLEAN                  NOT NULL,
    location    VARCHAR(300),
    meet_url    VARCHAR(500),
    html_url    VARCHAR(500),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (account_id, external_id)
);

CREATE INDEX idx_calendar_events_user ON calendar_events (user_id, starts_at);

-- Projeto de trabalho (ex.: um por empresa) com atalhos em JSON e a conta Google das reuniões dele.
CREATE TABLE dev_projects (
    id                  UUID PRIMARY KEY,
    user_id             UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name                VARCHAR(60)              NOT NULL,
    color               VARCHAR(7)               NOT NULL,
    description         VARCHAR(300),
    links               TEXT                     NOT NULL CHECK (length(links) <= 8000),
    calendar_account_id UUID REFERENCES integration_accounts (id) ON DELETE SET NULL,
    position            INT                      NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_dev_projects_user ON dev_projects (user_id, position);

-- Repositório do GitLab/GitHub ligado a um projeto, com as regras de raia (etiquetas da issue ao abrir/aceitar MR).
CREATE TABLE dev_repos (
    id              UUID PRIMARY KEY,
    project_id      UUID                     NOT NULL REFERENCES dev_projects (id) ON DELETE CASCADE,
    user_id         UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id      UUID                     NOT NULL REFERENCES integration_accounts (id) ON DELETE CASCADE,
    external_id     VARCHAR(200)             NOT NULL,
    full_name       VARCHAR(300)             NOT NULL,
    web_url         VARCHAR(500)             NOT NULL,
    default_branch  VARCHAR(200),
    import_issues   BOOLEAN                  NOT NULL,
    close_issues    BOOLEAN                  NOT NULL,
    label_mr_opened VARCHAR(100),
    label_mr_merged VARCHAR(100),
    labels_clear    VARCHAR(400),
    rules_since     TIMESTAMP WITH TIME ZONE NOT NULL,
    synced_at       TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (project_id, account_id, external_id)
);

CREATE INDEX idx_dev_repos_account ON dev_repos (account_id);

-- Retrato da última sincronização: MRs (seus e para revisar), issues atribuídas e pipelines do branch principal.
CREATE TABLE dev_items (
    id                UUID PRIMARY KEY,
    repo_id           UUID                     NOT NULL REFERENCES dev_repos (id) ON DELETE CASCADE,
    user_id           UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind              VARCHAR(10)              NOT NULL,
    external_id       VARCHAR(100)             NOT NULL,
    number            VARCHAR(20),
    title             VARCHAR(300)             NOT NULL,
    state             VARCHAR(20)              NOT NULL,
    web_url           VARCHAR(500)             NOT NULL,
    author            VARCHAR(100),
    mine              BOOLEAN                  NOT NULL,
    review            BOOLEAN                  NOT NULL,
    draft             BOOLEAN                  NOT NULL,
    pipeline          VARCHAR(20),
    branch            VARCHAR(200),
    labels            VARCHAR(1000),
    comments          INT                      NOT NULL,
    approved          BOOLEAN                  NOT NULL,
    linked_issues     VARCHAR(500),
    due_date          DATE,
    task_id           UUID REFERENCES tasks (id) ON DELETE SET NULL,
    task_linked       BOOLEAN                  NOT NULL,
    remote_updated_at TIMESTAMP WITH TIME ZONE,
    merged_at         TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (repo_id, kind, external_id)
);

CREATE INDEX idx_dev_items_user ON dev_items (user_id, kind);
CREATE INDEX idx_dev_items_task ON dev_items (task_id);

-- Movimentos de raia já feitos: um por MR e evento, para nunca repetir.
CREATE TABLE dev_automations (
    id         UUID PRIMARY KEY,
    repo_id    UUID                     NOT NULL REFERENCES dev_repos (id) ON DELETE CASCADE,
    mr_id      VARCHAR(100)             NOT NULL,
    event      VARCHAR(10)              NOT NULL,
    issues     VARCHAR(500)             NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (repo_id, mr_id, event)
);

-- Atividade por dia e repositório (commits, MRs abertos e aceitos, revisões) para o gráfico e o daily.
CREATE TABLE dev_activity (
    id             UUID PRIMARY KEY,
    repo_id        UUID                     NOT NULL REFERENCES dev_repos (id) ON DELETE CASCADE,
    user_id        UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day            DATE                     NOT NULL,
    commits        INT                      NOT NULL,
    merge_requests INT                      NOT NULL,
    merged         INT                      NOT NULL,
    reviews        INT                      NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (repo_id, day)
);

CREATE INDEX idx_dev_activity_user ON dev_activity (user_id, day);
