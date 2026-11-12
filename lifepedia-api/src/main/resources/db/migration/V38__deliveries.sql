-- Endereços de entrega da pessoa (reutilizados nas entregas).
CREATE TABLE delivery_addresses (
    id         UUID PRIMARY KEY,
    user_id    UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    label      VARCHAR(40)              NOT NULL,
    recipient  VARCHAR(120),
    cep        VARCHAR(8)               NOT NULL,
    street     VARCHAR(160)             NOT NULL,
    number     VARCHAR(20),
    complement VARCHAR(80),
    district   VARCHAR(80),
    city       VARCHAR(80)              NOT NULL,
    state      VARCHAR(2)               NOT NULL,
    is_default BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_delivery_addresses_user ON delivery_addresses (user_id);

-- Entregas rastreadas (17TRACK). group_id = compartilhada com um grupo (membros veem e recebem os avisos).
-- events = eventos do rastreio em JSON (mais novo primeiro).
CREATE TABLE deliveries (
    id                UUID PRIMARY KEY,
    user_id           UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    group_id          UUID REFERENCES user_groups (id) ON DELETE SET NULL,
    address_id        UUID REFERENCES delivery_addresses (id) ON DELETE SET NULL,
    name              VARCHAR(80)              NOT NULL,
    tracking_code     VARCHAR(40)              NOT NULL,
    image_key         VARCHAR(80),
    status            VARCHAR(20)              NOT NULL,
    sub_status        VARCHAR(60),
    tax_pending       BOOLEAN                  NOT NULL DEFAULT FALSE,
    carrier_key       INTEGER,
    carrier_name      VARCHAR(80),
    carrier_url       VARCHAR(300),
    latest_at         TIMESTAMP WITH TIME ZONE,
    latest_text       VARCHAR(400),
    latest_location   VARCHAR(160),
    origin            VARCHAR(160),
    estimated_from    DATE,
    estimated_to      DATE,
    delivered_at      TIMESTAMP WITH TIME ZONE,
    events            TEXT CHECK (length(events) <= 60000),
    registered        BOOLEAN                  NOT NULL DEFAULT FALSE,
    last_sync_at      TIMESTAMP WITH TIME ZONE,
    sync_error        VARCHAR(200),
    notified_status   VARCHAR(20),
    stall_warned      BOOLEAN                  NOT NULL DEFAULT FALSE,
    archived_at       TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_delivery_code UNIQUE (user_id, tracking_code)
);

CREATE INDEX idx_deliveries_user ON deliveries (user_id, archived_at);
CREATE INDEX idx_deliveries_group ON deliveries (group_id) WHERE group_id IS NOT NULL;
CREATE INDEX idx_deliveries_sync ON deliveries (last_sync_at) WHERE archived_at IS NULL;
