-- Grupos de usuários: membros, convites diretos e link de convite.
CREATE TABLE user_groups (
    id         UUID PRIMARY KEY,
    name       VARCHAR(60) NOT NULL,
    -- Arquivo da imagem no storage (nulo = sem imagem)
    image_key  VARCHAR(64),
    created_by UUID        NOT NULL REFERENCES users (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE group_members (
    id         UUID PRIMARY KEY,
    group_id   UUID        NOT NULL REFERENCES user_groups (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role       VARCHAR(10) NOT NULL CHECK (role IN ('ADMIN', 'MEMBER')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_group_members UNIQUE (group_id, user_id)
);

CREATE INDEX idx_group_members_user ON group_members (user_id);

CREATE TABLE group_invites (
    id         UUID PRIMARY KEY,
    group_id   UUID        NOT NULL REFERENCES user_groups (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    invited_by UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status     VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Um convite pendente por pessoa e grupo.
CREATE UNIQUE INDEX uk_group_invites_pending ON group_invites (group_id, user_id) WHERE status = 'PENDING';
CREATE INDEX idx_group_invites_user ON group_invites (user_id, status);

-- Um link ativo por grupo; gerar outro substitui.
CREATE TABLE group_invite_links (
    id         UUID PRIMARY KEY,
    group_id   UUID        NOT NULL REFERENCES user_groups (id) ON DELETE CASCADE,
    token      VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_group_invite_links_group UNIQUE (group_id),
    CONSTRAINT uk_group_invite_links_token UNIQUE (token)
);
