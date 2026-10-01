-- Descrição como veio no extrato/fatura (importação); a editável fica em description.
ALTER TABLE transactions ADD COLUMN original_description VARCHAR(200);
