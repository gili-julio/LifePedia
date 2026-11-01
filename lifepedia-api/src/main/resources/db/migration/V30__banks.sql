-- Banco de cada item: código do catálogo (Bank), nome livre só em OTHER e conta ou cartão. Nulo = sem banco.
ALTER TABLE transactions
    ADD COLUMN bank_code VARCHAR(30),
    ADD COLUMN bank_name VARCHAR(60),
    ADD COLUMN bank_kind VARCHAR(10);

ALTER TABLE recurring_entries
    ADD COLUMN bank_code VARCHAR(30),
    ADD COLUMN bank_name VARCHAR(60),
    ADD COLUMN bank_kind VARCHAR(10);

ALTER TABLE group_transactions
    ADD COLUMN bank_code VARCHAR(30),
    ADD COLUMN bank_name VARCHAR(60),
    ADD COLUMN bank_kind VARCHAR(10);

ALTER TABLE group_recurring_entries
    ADD COLUMN bank_code VARCHAR(30),
    ADD COLUMN bank_name VARCHAR(60),
    ADD COLUMN bank_kind VARCHAR(10);

-- O nome continua em financings.bank; o código liga ao catálogo (e ao logo).
ALTER TABLE financings ADD COLUMN bank_code VARCHAR(30);

CREATE INDEX idx_transactions_user_bank ON transactions (user_id, bank_code);
CREATE INDEX idx_recurring_entries_user_bank ON recurring_entries (user_id, bank_code);
