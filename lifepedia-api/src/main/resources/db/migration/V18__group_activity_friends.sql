-- Grupos: última atividade (ordem da sidebar) e grupos fixados por membro (até 3).
ALTER TABLE user_groups ADD COLUMN last_activity_at TIMESTAMP WITH TIME ZONE;
UPDATE user_groups SET last_activity_at = updated_at;
ALTER TABLE user_groups ALTER COLUMN last_activity_at SET NOT NULL;

ALTER TABLE group_members ADD COLUMN pinned_at TIMESTAMP WITH TIME ZONE;

-- Amizades: pedido pendente ou aceito (recusar, cancelar e desfazer apagam a linha). Um registro por par.
CREATE TABLE friendships (
    id           UUID PRIMARY KEY,
    requester_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    addressee_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status       VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED')),
    accepted_at  TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (requester_id <> addressee_id)
);

CREATE UNIQUE INDEX uk_friendships_pair ON friendships (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id));
CREATE INDEX idx_friendships_requester ON friendships (requester_id, status);
CREATE INDEX idx_friendships_addressee ON friendships (addressee_id, status);

-- Conquistas: tarefas concluídas por pessoa.
CREATE INDEX idx_task_occurrences_completed_by ON task_occurrences (completed_by) WHERE status = 'DONE';
