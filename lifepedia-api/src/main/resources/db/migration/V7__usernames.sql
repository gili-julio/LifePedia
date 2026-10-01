-- Nome de usuário (@username): obrigatório no cadastro, sempre minúsculo e único.
-- Contas antigas ficam sem e completam no perfil (o front bloqueia o uso até lá).
ALTER TABLE users ADD COLUMN username VARCHAR(30);
ALTER TABLE users ADD CONSTRAINT uk_users_username UNIQUE (username);
ALTER TABLE users ADD CONSTRAINT ck_users_username CHECK (username ~ '^[a-z0-9]([a-z0-9._]{1,28})[a-z0-9]$');
