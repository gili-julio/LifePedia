-- Tarefas de grupo: responsável fixo ou rodízio em ordem fixa, responsável trocado numa ocorrência e comentários.

-- rotation_start: deslocamento do rodízio (a ocorrência de índice i é da posição (rotation_start + i) mod total;
-- em "a partir da conclusão" avança a cada ocorrência resolvida).
ALTER TABLE tasks
    ADD COLUMN assignee_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN rotation_start INTEGER NOT NULL DEFAULT 0 CHECK (rotation_start >= 0);

CREATE INDEX idx_tasks_assignee ON tasks (assignee_id) WHERE assignee_id IS NOT NULL;

CREATE TABLE task_rotation (
    task_id  UUID    NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    user_id  UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (task_id, position)
);

CREATE INDEX idx_task_rotation_user ON task_rotation (user_id);

-- Responsável só desta ocorrência (ou quem era o responsável quando foi resolvida); nulo = o da tarefa.
ALTER TABLE task_occurrences
    ADD COLUMN assignee_id UUID REFERENCES users (id) ON DELETE SET NULL;

CREATE INDEX idx_task_occurrences_assignee ON task_occurrences (assignee_id) WHERE assignee_id IS NOT NULL;

CREATE TABLE task_comments (
    id         UUID PRIMARY KEY,
    task_id    UUID          NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    user_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    body       VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_task_comments_task ON task_comments (task_id, created_at);
CREATE INDEX idx_task_comments_user ON task_comments (user_id);
