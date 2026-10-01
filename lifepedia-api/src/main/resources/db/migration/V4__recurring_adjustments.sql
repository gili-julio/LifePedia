-- Valor real de um fixo num mês específico (ex.: conta de luz que variou).
CREATE TABLE recurring_adjustments (
    id                 UUID PRIMARY KEY,
    user_id            UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    recurring_entry_id UUID           NOT NULL REFERENCES recurring_entries (id) ON DELETE CASCADE,
    month              DATE           NOT NULL,
    amount             NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_recurring_adjustments_entry_month UNIQUE (recurring_entry_id, month)
);

CREATE INDEX idx_recurring_adjustments_user ON recurring_adjustments (user_id);
