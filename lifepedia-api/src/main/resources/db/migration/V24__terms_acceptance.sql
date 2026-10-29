-- Aceite dos Termos de uso e da Política de privacidade (LGPD): versão aceita e quando.
ALTER TABLE users ADD COLUMN terms_version VARCHAR(20);
ALTER TABLE users ADD COLUMN terms_accepted_at TIMESTAMP WITH TIME ZONE;
