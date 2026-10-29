-- Tarefa de grupo com vários responsáveis (substitui assignee_id), "todos do grupo" e modo de conclusão.
CREATE TABLE task_assignees (
    task_id UUID NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (task_id, user_id)
);

CREATE INDEX idx_task_assignees_user ON task_assignees (user_id);

INSERT INTO task_assignees (task_id, user_id)
SELECT id, assignee_id FROM tasks WHERE assignee_id IS NOT NULL;

DROP INDEX idx_tasks_assignee;

-- completion: ANY = basta um concluir; EACH = cada responsável conclui a sua parte.
ALTER TABLE tasks
    DROP COLUMN assignee_id,
    ADD COLUMN all_members BOOLEAN    NOT NULL DEFAULT FALSE,
    ADD COLUMN completion  VARCHAR(4) NOT NULL DEFAULT 'ANY' CHECK (completion IN ('ANY', 'EACH'));

-- "Cada um conclui a sua": quem já fez a sua parte na ocorrência.
CREATE TABLE task_occurrence_done (
    occurrence_id UUID NOT NULL REFERENCES task_occurrences (id) ON DELETE CASCADE,
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (occurrence_id, user_id)
);

CREATE INDEX idx_task_occurrence_done_user ON task_occurrence_done (user_id);

-- Horas visíveis na agenda de cada pessoa (sem linha = padrão do app).
CREATE TABLE agenda_settings (
    id         UUID PRIMARY KEY,
    user_id    UUID                     NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    day_start  SMALLINT                 NOT NULL CHECK (day_start BETWEEN 0 AND 23),
    day_end    SMALLINT                 NOT NULL CHECK (day_end BETWEEN 1 AND 24),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (day_start < day_end)
);
