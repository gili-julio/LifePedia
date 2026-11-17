-- Veículos da pessoa. odometer = maior leitura registrada; fipe_* = referência na Tabela FIPE e o último valor lido.
CREATE TABLE vehicles (
    id              UUID PRIMARY KEY,
    user_id         UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type            VARCHAR(12)              NOT NULL,
    name            VARCHAR(60)              NOT NULL,
    brand           VARCHAR(60),
    model           VARCHAR(120),
    model_year      INTEGER,
    plate           VARCHAR(10),
    color           VARCHAR(30),
    fuel            VARCHAR(12),
    tank_liters     NUMERIC(6, 1),
    odometer        INTEGER                  NOT NULL DEFAULT 0,
    odometer_on     DATE,
    image_key       VARCHAR(80),
    fipe_kind       VARCHAR(12),
    fipe_brand      VARCHAR(10),
    fipe_model      VARCHAR(10),
    fipe_year       VARCHAR(12),
    fipe_code       VARCHAR(12),
    fipe_value      NUMERIC(12, 2),
    fipe_month      VARCHAR(30),
    fipe_checked_at TIMESTAMP WITH TIME ZONE,
    notes           TEXT CHECK (length(notes) <= 2000),
    archived_at     TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_vehicles_user ON vehicles (user_id);

-- Compartilhamento com uma pessoa (amiga) ou com um grupo do dono. VIEW = ver; LOG = lançar km, gastos e manutenção
-- feita; EDIT = também editar o veículo, os itens, as revisões e os documentos.
CREATE TABLE vehicle_shares (
    id         UUID PRIMARY KEY,
    vehicle_id UUID                     NOT NULL REFERENCES vehicles (id) ON DELETE CASCADE,
    user_id    UUID REFERENCES users (id) ON DELETE CASCADE,
    group_id   UUID REFERENCES user_groups (id) ON DELETE CASCADE,
    role       VARCHAR(10)              NOT NULL CHECK (role IN ('VIEW', 'LOG', 'EDIT')),
    created_by UUID                     REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((user_id IS NULL) <> (group_id IS NULL)),
    UNIQUE (vehicle_id, user_id),
    UNIQUE (vehicle_id, group_id)
);

CREATE INDEX idx_vehicle_shares_user ON vehicle_shares (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_vehicle_shares_group ON vehicle_shares (group_id) WHERE group_id IS NOT NULL;

-- Gastos do veículo (abastecimento, manutenção, documentos...). user_id = quem lançou (e pagou);
-- transaction_id = lançamento criado no financeiro dessa pessoa.
CREATE TABLE vehicle_expenses (
    id             UUID PRIMARY KEY,
    vehicle_id     UUID                     NOT NULL REFERENCES vehicles (id) ON DELETE CASCADE,
    user_id        UUID REFERENCES users (id) ON DELETE SET NULL,
    kind           VARCHAR(12)              NOT NULL,
    spent_on       DATE                     NOT NULL,
    amount         NUMERIC(12, 2)           NOT NULL CHECK (amount > 0),
    description    VARCHAR(120),
    odometer       INTEGER,
    liters         NUMERIC(7, 2),
    fuel           VARCHAR(12),
    full_tank      BOOLEAN                  NOT NULL DEFAULT FALSE,
    station        VARCHAR(60),
    notes          TEXT CHECK (length(notes) <= 2000),
    image_key      VARCHAR(80),
    transaction_id UUID REFERENCES transactions (id) ON DELETE SET NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_vehicle_expenses_vehicle ON vehicle_expenses (vehicle_id, spent_on);
CREATE INDEX idx_vehicle_expenses_transaction ON vehicle_expenses (transaction_id) WHERE transaction_id IS NOT NULL;

-- Leituras do odômetro (manual ou vindas de um gasto com km). Some junto com o gasto.
CREATE TABLE vehicle_readings (
    id         UUID PRIMARY KEY,
    vehicle_id UUID                     NOT NULL REFERENCES vehicles (id) ON DELETE CASCADE,
    user_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    expense_id UUID REFERENCES vehicle_expenses (id) ON DELETE CASCADE,
    km         INTEGER                  NOT NULL CHECK (km >= 0),
    read_on    DATE                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_vehicle_readings_vehicle ON vehicle_readings (vehicle_id, read_on);

-- Itens de manutenção periódica (a cada X km e/ou Y meses). warned = último aviso dado (SOON, DUE).
CREATE TABLE vehicle_items (
    id              UUID PRIMARY KEY,
    vehicle_id      UUID                     NOT NULL REFERENCES vehicles (id) ON DELETE CASCADE,
    name            VARCHAR(60)              NOT NULL,
    interval_km     INTEGER CHECK (interval_km > 0),
    interval_months INTEGER CHECK (interval_months > 0),
    last_km         INTEGER,
    last_on         DATE,
    notify          BOOLEAN                  NOT NULL DEFAULT TRUE,
    warned          VARCHAR(6),
    position        INTEGER                  NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (interval_km IS NOT NULL OR interval_months IS NOT NULL)
);

CREATE INDEX idx_vehicle_items_vehicle ON vehicle_items (vehicle_id);

-- Revisões do plano (a que vier primeiro: km ou data). done_on preenchido = feita.
CREATE TABLE vehicle_revisions (
    id         UUID PRIMARY KEY,
    vehicle_id UUID                     NOT NULL REFERENCES vehicles (id) ON DELETE CASCADE,
    name       VARCHAR(60)              NOT NULL,
    due_km     INTEGER,
    due_on     DATE,
    done_on    DATE,
    done_km    INTEGER,
    shop       VARCHAR(60),
    notes      TEXT CHECK (length(notes) <= 2000),
    expense_id UUID REFERENCES vehicle_expenses (id) ON DELETE SET NULL,
    warned     VARCHAR(6),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (due_km IS NOT NULL OR due_on IS NOT NULL)
);

CREATE INDEX idx_vehicle_revisions_vehicle ON vehicle_revisions (vehicle_id);

-- Histórico do que foi feito (item ou revisão), com o gasto quando lançado.
CREATE TABLE vehicle_service_logs (
    id          UUID PRIMARY KEY,
    vehicle_id  UUID                     NOT NULL REFERENCES vehicles (id) ON DELETE CASCADE,
    item_id     UUID REFERENCES vehicle_items (id) ON DELETE SET NULL,
    revision_id UUID REFERENCES vehicle_revisions (id) ON DELETE SET NULL,
    user_id     UUID REFERENCES users (id) ON DELETE SET NULL,
    expense_id  UUID REFERENCES vehicle_expenses (id) ON DELETE SET NULL,
    name        VARCHAR(60)              NOT NULL,
    done_on     DATE                     NOT NULL,
    km          INTEGER,
    notes       VARCHAR(500),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_vehicle_service_logs_vehicle ON vehicle_service_logs (vehicle_id, done_on);

-- Documentos com vencimento (IPVA, licenciamento, seguro, CNH...).
CREATE TABLE vehicle_documents (
    id         UUID PRIMARY KEY,
    vehicle_id UUID                     NOT NULL REFERENCES vehicles (id) ON DELETE CASCADE,
    kind       VARCHAR(12)              NOT NULL,
    name       VARCHAR(60)              NOT NULL,
    due_on     DATE                     NOT NULL,
    amount     NUMERIC(12, 2),
    notes      TEXT CHECK (length(notes) <= 2000),
    warned     VARCHAR(6),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_vehicle_documents_vehicle ON vehicle_documents (vehicle_id);
