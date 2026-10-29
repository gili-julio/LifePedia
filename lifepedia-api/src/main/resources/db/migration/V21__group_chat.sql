-- Chat do grupo. Apagar a mensagem limpa o texto e marca deleted_at (o lugar na conversa fica).
CREATE TABLE group_messages (
    id          UUID PRIMARY KEY,
    group_id    UUID                     NOT NULL REFERENCES user_groups (id) ON DELETE CASCADE,
    user_id     UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reply_to_id UUID REFERENCES group_messages (id) ON DELETE SET NULL,
    body        VARCHAR(2000),
    edited_at   TIMESTAMP WITH TIME ZONE,
    deleted_at  TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((deleted_at IS NULL) = (body IS NOT NULL))
);

CREATE INDEX idx_group_messages_group ON group_messages (group_id, created_at DESC, id DESC);

-- Até onde cada membro leu o chat (sem linha = desde que entrou no grupo).
CREATE TABLE group_chat_reads (
    group_id UUID                     NOT NULL REFERENCES user_groups (id) ON DELETE CASCADE,
    user_id  UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    read_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (group_id, user_id)
);
