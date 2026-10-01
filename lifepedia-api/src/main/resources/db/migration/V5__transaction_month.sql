-- Mês em que o lançamento conta (ex.: compra de agosto na fatura de setembro).
ALTER TABLE transactions ADD COLUMN month DATE;
UPDATE transactions SET month = date_trunc('month', date)::date;
ALTER TABLE transactions ALTER COLUMN month SET NOT NULL;
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_month CHECK (month = date_trunc('month', month)::date AND month >= date_trunc('month', date)::date);
CREATE INDEX idx_transactions_user_month ON transactions (user_id, month);
