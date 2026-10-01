-- Lançamentos fixos (recorrentes mensais): salário, aluguel, assinaturas...
CREATE TABLE recurring_entries (
    id           UUID PRIMARY KEY,
    user_id      UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    description  VARCHAR(120)  NOT NULL,
    type         VARCHAR(10)   NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    category     VARCHAR(30)   NOT NULL,
    amount       NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    day_of_month INTEGER  CHECK (day_of_month BETWEEN 1 AND 31),
    start_month  DATE          NOT NULL,
    end_month    DATE,
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    notes        VARCHAR(500),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_recurring_period CHECK (end_month IS NULL OR end_month >= start_month)
);

CREATE INDEX idx_recurring_entries_user ON recurring_entries (user_id);

-- Lançamentos ocasionais (avulsos): uma compra, um freela, um presente...
CREATE TABLE transactions (
    id          UUID PRIMARY KEY,
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    description VARCHAR(120)  NOT NULL,
    type        VARCHAR(10)   NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    category    VARCHAR(30)   NOT NULL,
    amount      NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    date        DATE          NOT NULL,
    notes       VARCHAR(500),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_transactions_user_date ON transactions (user_id, date);
