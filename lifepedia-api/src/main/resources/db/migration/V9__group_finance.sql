-- Itens financeiros levados para grupos. Cada linha guarda os dados do item:
-- sincronizado (source_id preenchido) = espelho do item pessoal, atualizado junto e excluído junto;
-- cópia (source_id nulo) = independente, editável só por quem levou.
-- category_id é a categoria de quem levou; origin_id é o item pessoal de onde veio.
CREATE TABLE group_transactions (
    id          UUID PRIMARY KEY,
    group_id    UUID           NOT NULL REFERENCES user_groups (id) ON DELETE CASCADE,
    user_id     UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source_id   UUID           REFERENCES transactions (id) ON DELETE CASCADE,
    origin_id   UUID,
    description VARCHAR(120)   NOT NULL,
    type        VARCHAR(10)    NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    category_id UUID           NOT NULL REFERENCES categories (id),
    amount      NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    date        DATE           NOT NULL,
    month       DATE           NOT NULL,
    notes       VARCHAR(500),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_group_transactions_source UNIQUE (group_id, source_id),
    CONSTRAINT ck_group_transactions_month CHECK (month = date_trunc('month', month)::date AND month >= date_trunc('month', date)::date)
);

CREATE INDEX idx_group_transactions_month ON group_transactions (group_id, month);
CREATE INDEX idx_group_transactions_user ON group_transactions (user_id);

CREATE TABLE group_recurring_entries (
    id           UUID PRIMARY KEY,
    group_id     UUID           NOT NULL REFERENCES user_groups (id) ON DELETE CASCADE,
    user_id      UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source_id    UUID           REFERENCES recurring_entries (id) ON DELETE CASCADE,
    origin_id    UUID,
    description  VARCHAR(120)   NOT NULL,
    type         VARCHAR(10)    NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    category_id  UUID           NOT NULL REFERENCES categories (id),
    amount       NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    day_of_month INTEGER CHECK (day_of_month BETWEEN 1 AND 31),
    start_month  DATE           NOT NULL,
    end_month    DATE,
    active       BOOLEAN        NOT NULL DEFAULT TRUE,
    notes        VARCHAR(500),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_group_recurring_source UNIQUE (group_id, source_id),
    CONSTRAINT ck_group_recurring_period CHECK (end_month IS NULL OR end_month >= start_month)
);

CREATE INDEX idx_group_recurring_group ON group_recurring_entries (group_id);
CREATE INDEX idx_group_recurring_user ON group_recurring_entries (user_id);
