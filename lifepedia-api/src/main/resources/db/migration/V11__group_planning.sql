-- Planejamento do grupo: coisas que o grupo quer comprar ou investir até um mês, simuladas contra o saldo previsto.

-- Base da simulação (uma linha por grupo, criada no primeiro ajuste).
-- average_months: meses usados na média dos ocasionais (0 = só fixos); manual_balance substitui o cálculo.
CREATE TABLE plan_settings (
    id             UUID PRIMARY KEY,
    group_id       UUID           NOT NULL UNIQUE REFERENCES user_groups (id) ON DELETE CASCADE,
    reserve        NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (reserve >= 0),
    average_months INTEGER        NOT NULL DEFAULT 3 CHECK (average_months IN (0, 3, 6, 12)),
    manual_balance NUMERIC(14, 2),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

-- position = prioridade (menor recebe o dinheiro primeiro). Parcelado: parcelas a partir de target_month.
-- link_*: dados lidos da página do link; image_key: arquivo em STORAGE_DIR/plan-items.
-- acquired_on preenchido = já comprado/feito (sai da simulação).
CREATE TABLE plan_items (
    id              UUID PRIMARY KEY,
    group_id        UUID           NOT NULL REFERENCES user_groups (id) ON DELETE CASCADE,
    user_id         UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name            VARCHAR(120)   NOT NULL,
    kind            VARCHAR(12)    NOT NULL CHECK (kind IN ('PURCHASE', 'INVESTMENT')),
    amount          NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    target_month    DATE           NOT NULL CHECK (target_month = date_trunc('month', target_month)::date),
    installments    INTEGER        NOT NULL DEFAULT 1 CHECK (installments BETWEEN 1 AND 120),
    position        INTEGER        NOT NULL,
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    notes           VARCHAR(500),
    url             VARCHAR(2048),
    link_title      VARCHAR(300),
    link_site       VARCHAR(120),
    link_price      NUMERIC(14, 2),
    image_key       VARCHAR(32),
    acquired_on     DATE,
    acquired_amount NUMERIC(14, 2),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_plan_items_group ON plan_items (group_id, position);
CREATE INDEX idx_plan_items_user ON plan_items (user_id);

-- Cenário = conjunto nomeado de itens considerados, para comparar e aplicar.
CREATE TABLE plan_scenarios (
    id         UUID PRIMARY KEY,
    group_id   UUID        NOT NULL REFERENCES user_groups (id) ON DELETE CASCADE,
    user_id    UUID        REFERENCES users (id) ON DELETE SET NULL,
    name       VARCHAR(60) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_plan_scenarios_group ON plan_scenarios (group_id);

CREATE TABLE plan_scenario_items (
    scenario_id UUID NOT NULL REFERENCES plan_scenarios (id) ON DELETE CASCADE,
    item_id     UUID NOT NULL REFERENCES plan_items (id) ON DELETE CASCADE,
    PRIMARY KEY (scenario_id, item_id)
);

CREATE INDEX idx_plan_scenario_items_item ON plan_scenario_items (item_id);
