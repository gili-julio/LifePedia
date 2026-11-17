-- Cartões de crédito da pessoa: um por banco (bank_code + bank_name em OTHER), ligado aos lançamentos com banco
-- do tipo CARD. A fatura é identificada pelo mês do vencimento; compra no dia do fechamento ou depois vai para a seguinte.
CREATE TABLE finance_cards (
    id           UUID PRIMARY KEY,
    user_id      UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    bank_code    VARCHAR(30)              NOT NULL,
    bank_name    VARCHAR(60),
    nickname     VARCHAR(40),
    last_digits  VARCHAR(4) CHECK (last_digits ~ '^[0-9]{4}$'),
    color        VARCHAR(7) CHECK (color ~ '^#[0-9a-fA-F]{6}$'),
    closing_day  INTEGER                  NOT NULL CHECK (closing_day BETWEEN 1 AND 31),
    due_day      INTEGER                  NOT NULL CHECK (due_day BETWEEN 1 AND 31),
    credit_limit NUMERIC(14, 2) CHECK (credit_limit > 0),
    budget       NUMERIC(14, 2) CHECK (budget > 0),
    reminders    BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX ux_finance_cards_bank ON finance_cards (user_id, bank_code, lower(coalesce(bank_name, '')));

-- Grupos que veem a fatura aberta do cartão e quanto do gasto do grupo está nele.
CREATE TABLE finance_card_groups (
    card_id  UUID NOT NULL REFERENCES finance_cards (id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES user_groups (id) ON DELETE CASCADE,
    PRIMARY KEY (card_id, group_id)
);

CREATE INDEX idx_finance_card_groups_group ON finance_card_groups (group_id);

-- Contas correntes: saldo informado numa data; o resto é projetado pelos lançamentos.
CREATE TABLE finance_accounts (
    id         UUID PRIMARY KEY,
    user_id    UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    bank_code  VARCHAR(30)              NOT NULL,
    bank_name  VARCHAR(60),
    nickname   VARCHAR(40),
    balance    NUMERIC(14, 2)           NOT NULL,
    balance_on DATE                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX ux_finance_accounts_bank ON finance_accounts (user_id, bank_code, lower(coalesce(bank_name, '')));

-- Conta que paga a fatura (nula = a do mesmo banco, se houver).
ALTER TABLE finance_cards ADD COLUMN account_id UUID REFERENCES finance_accounts (id) ON DELETE SET NULL;

-- Estado de cada fatura (criado quando há algo a guardar): pagamento e avisos já enviados.
-- month = 1º dia do mês do vencimento.
CREATE TABLE finance_invoices (
    id             UUID PRIMARY KEY,
    card_id        UUID                     NOT NULL REFERENCES finance_cards (id) ON DELETE CASCADE,
    month          DATE                     NOT NULL CHECK (month = date_trunc('month', month)::date),
    paid_on        DATE,
    paid_amount    NUMERIC(14, 2) CHECK (paid_amount >= 0),
    paid_account   UUID REFERENCES finance_accounts (id) ON DELETE SET NULL,
    warned_closing BOOLEAN                  NOT NULL DEFAULT FALSE,
    warned_due     BOOLEAN                  NOT NULL DEFAULT FALSE,
    warned_budget  INTEGER                  NOT NULL DEFAULT 0,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (card_id, month)
);

-- Conferido com a fatura fechada (conciliação na importação).
ALTER TABLE transactions ADD COLUMN checked BOOLEAN NOT NULL DEFAULT FALSE;
