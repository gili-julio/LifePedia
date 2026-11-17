-- Busca geral: texto sem acento e em minúsculas (search_norm) e semelhança por trigramas (erro de digitação).
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- unaccent não é IMMUTABLE; com o dicionário fixo a função pode ser marcada assim.
CREATE OR REPLACE FUNCTION search_norm(value TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT AS
$$ SELECT lower(public.unaccent('public.unaccent'::regdictionary, value)) $$;
