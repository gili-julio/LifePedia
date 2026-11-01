-- Taxas médias por banco divulgadas pelo Banco Central (última divulgação de cada modalidade).
CREATE TABLE financing_rates (
    id           UUID PRIMARY KEY,
    modality     VARCHAR(20)              NOT NULL,
    period_start DATE                     NOT NULL,
    position     INTEGER                  NOT NULL,
    bank         VARCHAR(160)             NOT NULL,
    monthly_rate NUMERIC(8, 4)            NOT NULL,
    yearly_rate  NUMERIC(8, 4)            NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_financing_rates_modality ON financing_rates (modality);

-- Variação mensal (%) dos índices de correção do saldo (TR, IPCA, INCC).
CREATE TABLE financing_index_values (
    id         UUID PRIMARY KEY,
    index_name VARCHAR(10)              NOT NULL,
    month      DATE                     NOT NULL,
    rate       NUMERIC(8, 4)            NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_financing_index_month UNIQUE (index_name, month)
);

-- Simulações salvas: pessoais (user_id) ou de um grupo (group_id); input = pedido da simulação em JSON.
CREATE TABLE financing_simulations (
    id         UUID PRIMARY KEY,
    user_id    UUID REFERENCES users (id) ON DELETE CASCADE,
    group_id   UUID REFERENCES user_groups (id) ON DELETE CASCADE,
    created_by UUID REFERENCES users (id) ON DELETE SET NULL,
    name       VARCHAR(120)             NOT NULL,
    input      TEXT                     NOT NULL CHECK (length(input) <= 20000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((user_id IS NULL) <> (group_id IS NULL))
);

CREATE INDEX idx_financing_simulations_user ON financing_simulations (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_financing_simulations_group ON financing_simulations (group_id) WHERE group_id IS NOT NULL;

-- Financiamento em andamento (contrato). monthly_rate e insurance_rate em % ao mês.
CREATE TABLE financings (
    id               UUID PRIMARY KEY,
    user_id          UUID REFERENCES users (id) ON DELETE CASCADE,
    group_id         UUID REFERENCES user_groups (id) ON DELETE CASCADE,
    created_by       UUID REFERENCES users (id) ON DELETE SET NULL,
    name             VARCHAR(120)             NOT NULL,
    kind             VARCHAR(20)              NOT NULL CHECK (kind IN ('HOUSE', 'APARTMENT', 'CAR', 'MOTORCYCLE', 'OTHER')),
    item_condition   VARCHAR(10)              NOT NULL CHECK (item_condition IN ('NEW', 'USED')),
    price            NUMERIC(14, 2),
    down_payment     NUMERIC(14, 2),
    fgts             NUMERIC(14, 2),
    financed         NUMERIC(14, 2)           NOT NULL CHECK (financed > 0),
    monthly_rate     NUMERIC(8, 4)            NOT NULL CHECK (monthly_rate >= 0 AND monthly_rate <= 20),
    system           VARCHAR(5)               NOT NULL CHECK (system IN ('SAC', 'PRICE')),
    months           INTEGER                  NOT NULL CHECK (months BETWEEN 1 AND 420),
    first_month      DATE                     NOT NULL,
    due_day          INTEGER                  NOT NULL CHECK (due_day BETWEEN 1 AND 31),
    insurance_rate   NUMERIC(8, 5)            NOT NULL DEFAULT 0 CHECK (insurance_rate >= 0 AND insurance_rate <= 5),
    monthly_fee      NUMERIC(14, 2)           NOT NULL DEFAULT 0 CHECK (monthly_fee >= 0),
    correction_index VARCHAR(10)              NOT NULL DEFAULT 'NONE' CHECK (correction_index IN ('NONE', 'TR', 'IPCA', 'INCC')),
    bank             VARCHAR(160),
    reminder_days    INTEGER CHECK (reminder_days BETWEEN 0 AND 15),
    notes            VARCHAR(2000),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((user_id IS NULL) <> (group_id IS NULL))
);

CREATE INDEX idx_financings_user ON financings (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_financings_group ON financings (group_id) WHERE group_id IS NOT NULL;

-- Quem paga e quanto (%); recurring_entry_id = fixo pessoal com a parte da pessoa, se ela ligou.
CREATE TABLE financing_members (
    id                 UUID PRIMARY KEY,
    financing_id       UUID                     NOT NULL REFERENCES financings (id) ON DELETE CASCADE,
    user_id            UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    share              NUMERIC(5, 2)            NOT NULL CHECK (share >= 0 AND share <= 100),
    recurring_entry_id UUID REFERENCES recurring_entries (id) ON DELETE SET NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_financing_members UNIQUE (financing_id, user_id)
);

CREATE INDEX idx_financing_members_user ON financing_members (user_id);
CREATE INDEX idx_financing_members_recurring ON financing_members (recurring_entry_id) WHERE recurring_entry_id IS NOT NULL;

-- Amortização extra: TERM reduz o prazo, PAYMENT reduz a parcela.
CREATE TABLE financing_extra_payments (
    id           UUID PRIMARY KEY,
    financing_id UUID                     NOT NULL REFERENCES financings (id) ON DELETE CASCADE,
    month        DATE                     NOT NULL,
    amount       NUMERIC(14, 2)           NOT NULL CHECK (amount > 0),
    mode         VARCHAR(10)              NOT NULL CHECK (mode IN ('TERM', 'PAYMENT')),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_financing_extra_payments ON financing_extra_payments (financing_id);

-- Gastos do bem (IPVA, seguro, revisão, IPTU, condomínio...). Anual: due_month obrigatório.
CREATE TABLE financing_costs (
    id           UUID PRIMARY KEY,
    financing_id UUID                     NOT NULL REFERENCES financings (id) ON DELETE CASCADE,
    name         VARCHAR(80)              NOT NULL,
    amount       NUMERIC(14, 2)           NOT NULL CHECK (amount > 0),
    frequency    VARCHAR(10)              NOT NULL CHECK (frequency IN ('MONTHLY', 'YEARLY')),
    due_month    INTEGER CHECK (due_month BETWEEN 1 AND 12),
    due_day      INTEGER                  NOT NULL CHECK (due_day BETWEEN 1 AND 31),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (frequency = 'MONTHLY' OR due_month IS NOT NULL)
);

CREATE INDEX idx_financing_costs ON financing_costs (financing_id);
