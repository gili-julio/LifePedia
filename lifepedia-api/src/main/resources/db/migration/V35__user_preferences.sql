-- Preferências da conta: menu só com ícones e módulos escondidos (lista separada por vírgula de AppModule).
ALTER TABLE users
    ADD COLUMN sidebar_icons  BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN hidden_modules VARCHAR(400) NOT NULL DEFAULT '';
