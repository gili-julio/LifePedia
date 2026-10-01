-- Preço/foto de referência: produto parecido achado em outro site quando a loja do link não libera os dados.
ALTER TABLE plan_items
    ADD COLUMN reference_site  VARCHAR(60),
    ADD COLUMN reference_title VARCHAR(300),
    ADD COLUMN reference_url   VARCHAR(2048),
    ADD COLUMN reference_price NUMERIC(14, 2);
