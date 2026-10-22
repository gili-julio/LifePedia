-- Foto de perfil: arquivo no storage (nulo = sem foto).
ALTER TABLE users ADD COLUMN image_key VARCHAR(64);
