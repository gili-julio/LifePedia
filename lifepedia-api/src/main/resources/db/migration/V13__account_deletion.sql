-- Quem criou o grupo pode excluir a conta; o grupo continua com os outros membros.
ALTER TABLE user_groups ALTER COLUMN created_by DROP NOT NULL;
ALTER TABLE user_groups DROP CONSTRAINT user_groups_created_by_fkey;
ALTER TABLE user_groups ADD CONSTRAINT user_groups_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL;
