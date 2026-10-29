-- Sessões no banco: logout revoga o token; "lembrar de mim" dura 30 dias.
CREATE TABLE user_sessions (
    id         UUID PRIMARY KEY,
    user_id    UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    remember   BOOLEAN                  NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_user_sessions_user ON user_sessions (user_id);
CREATE INDEX idx_user_sessions_expires ON user_sessions (expires_at);

-- Login com Google: conta vinculada pelo "sub" do Google; conta criada pelo Google pode não ter senha.
ALTER TABLE users ADD COLUMN google_sub VARCHAR(255);
CREATE UNIQUE INDEX uk_users_google_sub ON users (google_sub);
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- Observações até 10 mil caracteres.
ALTER TABLE transactions ALTER COLUMN notes TYPE VARCHAR(10000);
ALTER TABLE recurring_entries ALTER COLUMN notes TYPE VARCHAR(10000);
ALTER TABLE group_transactions ALTER COLUMN notes TYPE VARCHAR(10000);
ALTER TABLE group_recurring_entries ALTER COLUMN notes TYPE VARCHAR(10000);
ALTER TABLE plan_items ALTER COLUMN notes TYPE VARCHAR(10000);
ALTER TABLE tasks ALTER COLUMN notes TYPE VARCHAR(10000);
ALTER TABLE task_occurrences ALTER COLUMN notes TYPE VARCHAR(10000);
