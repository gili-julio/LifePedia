-- Acompanhamento de preço: item pessoal (lista de desejos) ou do grupo (opcionalmente ligado a um item do planejamento).
-- O preço vem do Buscapé (lojas e histórico), de links das lojas e de preços anotados à mão.
CREATE TABLE price_watches (
    id              UUID PRIMARY KEY,
    user_id         UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    group_id        UUID REFERENCES user_groups (id) ON DELETE CASCADE,
    plan_item_id    UUID REFERENCES plan_items (id) ON DELETE SET NULL,
    name            VARCHAR(160)             NOT NULL,
    buscape_url     VARCHAR(500),
    image_key       VARCHAR(80),
    target_price    NUMERIC(14, 2) CHECK (target_price > 0),
    drop_percent    INTEGER                  NOT NULL DEFAULT 10 CHECK (drop_percent BETWEEN 1 AND 90),
    notify_group    BOOLEAN                  NOT NULL DEFAULT FALSE,
    update_plan     BOOLEAN                  NOT NULL DEFAULT FALSE,
    active          BOOLEAN                  NOT NULL DEFAULT TRUE,
    offers          TEXT CHECK (length(offers) <= 60000),
    best_price      NUMERIC(14, 2),
    best_store      VARCHAR(80),
    best_url        VARCHAR(2048),
    store_count     INTEGER,
    alerted_price   NUMERIC(14, 2),
    last_checked_at TIMESTAMP WITH TIME ZONE,
    last_error      VARCHAR(200),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_price_watches_user ON price_watches (user_id);
CREATE INDEX idx_price_watches_group ON price_watches (group_id);
CREATE INDEX idx_price_watches_checked ON price_watches (last_checked_at);
CREATE UNIQUE INDEX uq_price_watches_plan_item ON price_watches (plan_item_id) WHERE plan_item_id IS NOT NULL;

-- Links de lojas colados pela pessoa (lidos a cada consulta; loja que bloqueia fica com o erro).
CREATE TABLE price_links (
    id              UUID PRIMARY KEY,
    watch_id        UUID                     NOT NULL REFERENCES price_watches (id) ON DELETE CASCADE,
    url             VARCHAR(2048)            NOT NULL,
    store           VARCHAR(80),
    last_price      NUMERIC(14, 2),
    last_checked_at TIMESTAMP WITH TIME ZONE,
    last_error      VARCHAR(200),
    created_by      UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (watch_id, url)
);

-- Histórico: um preço por loja, dia e origem.
CREATE TABLE price_points (
    id         UUID PRIMARY KEY,
    watch_id   UUID                     NOT NULL REFERENCES price_watches (id) ON DELETE CASCADE,
    day        DATE                     NOT NULL,
    store      VARCHAR(80)              NOT NULL,
    price      NUMERIC(14, 2)           NOT NULL CHECK (price > 0),
    source     VARCHAR(10)              NOT NULL CHECK (source IN ('BUSCAPE', 'LINK', 'MANUAL')),
    url        VARCHAR(2048),
    created_by UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (watch_id, day, store, source)
);

CREATE INDEX idx_price_points_watch_day ON price_points (watch_id, day);
