-- Notas em markdown: pessoais (user_id) ou de um grupo (group_id), nunca os dois.
CREATE TABLE note_folders (
    id         UUID PRIMARY KEY,
    user_id    UUID REFERENCES users (id) ON DELETE CASCADE,
    group_id   UUID REFERENCES user_groups (id) ON DELETE CASCADE,
    name       VARCHAR(80)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((user_id IS NULL) <> (group_id IS NULL))
);

CREATE UNIQUE INDEX uq_note_folders_user_name ON note_folders (user_id, lower(name)) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX uq_note_folders_group_name ON note_folders (group_id, lower(name)) WHERE group_id IS NOT NULL;

CREATE TABLE note_tags (
    id         UUID PRIMARY KEY,
    user_id    UUID REFERENCES users (id) ON DELETE CASCADE,
    group_id   UUID REFERENCES user_groups (id) ON DELETE CASCADE,
    name       VARCHAR(40)              NOT NULL,
    color      VARCHAR(7)               NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((user_id IS NULL) <> (group_id IS NULL))
);

CREATE UNIQUE INDEX uq_note_tags_user_name ON note_tags (user_id, lower(name)) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX uq_note_tags_group_name ON note_tags (group_id, lower(name)) WHERE group_id IS NOT NULL;

-- version: conferida a cada gravação (duas abas ou duas pessoas editando a mesma nota).
CREATE TABLE notes (
    id            UUID PRIMARY KEY,
    user_id       UUID REFERENCES users (id) ON DELETE CASCADE,
    group_id      UUID REFERENCES user_groups (id) ON DELETE CASCADE,
    created_by    UUID REFERENCES users (id) ON DELETE SET NULL,
    edited_by     UUID REFERENCES users (id) ON DELETE SET NULL,
    folder_id     UUID REFERENCES note_folders (id) ON DELETE SET NULL,
    title         VARCHAR(200)             NOT NULL,
    content       TEXT                     NOT NULL CHECK (length(content) <= 200000),
    icon          VARCHAR(16),
    color         VARCHAR(7),
    template      BOOLEAN                  NOT NULL DEFAULT FALSE,
    pinned_at     TIMESTAMP WITH TIME ZONE,
    archived_at   TIMESTAMP WITH TIME ZONE,
    deleted_at    TIMESTAMP WITH TIME ZONE,
    task_id       UUID REFERENCES tasks (id) ON DELETE SET NULL,
    commitment_id UUID REFERENCES commitments (id) ON DELETE SET NULL,
    version       BIGINT                   NOT NULL DEFAULT 0,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((user_id IS NULL) <> (group_id IS NULL)),
    CHECK (NOT template OR user_id IS NOT NULL),
    CHECK (commitment_id IS NULL OR user_id IS NOT NULL)
);

CREATE INDEX idx_notes_user ON notes (user_id);
CREATE INDEX idx_notes_group ON notes (group_id);
CREATE INDEX idx_notes_task ON notes (task_id);
CREATE INDEX idx_notes_commitment ON notes (commitment_id);
CREATE INDEX idx_notes_deleted ON notes (deleted_at) WHERE deleted_at IS NOT NULL;

CREATE TABLE note_tag_links (
    note_id UUID NOT NULL REFERENCES notes (id) ON DELETE CASCADE,
    tag_id  UUID NOT NULL REFERENCES note_tags (id) ON DELETE CASCADE,
    PRIMARY KEY (note_id, tag_id)
);

CREATE INDEX idx_note_tag_links_tag ON note_tag_links (tag_id);

-- Fotos dentro da nota (arquivo em STORAGE_DIR/notes, nome = image_key).
CREATE TABLE note_images (
    id         UUID PRIMARY KEY,
    note_id    UUID                     NOT NULL REFERENCES notes (id) ON DELETE CASCADE,
    image_key  VARCHAR(32)              NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_note_images_note ON note_images (note_id);

-- Quem fez o quê na nota; edições seguidas da mesma pessoa viram uma linha só (updated_at = última).
CREATE TABLE note_activities (
    id         UUID PRIMARY KEY,
    note_id    UUID                     NOT NULL REFERENCES notes (id) ON DELETE CASCADE,
    user_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    action     VARCHAR(12)              NOT NULL
        CHECK (action IN ('CREATED', 'EDITED', 'ARCHIVED', 'UNARCHIVED', 'TRASHED', 'RESTORED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_note_activities_note ON note_activities (note_id, updated_at DESC);
