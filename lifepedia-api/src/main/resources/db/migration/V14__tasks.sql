-- Tarefas: pessoais (user_id) ou de um grupo (group_id), nunca os dois.
-- Repetição é uma regra na tarefa; as ocorrências são geradas na leitura e task_occurrences guarda só o que
-- mudou em cada uma (concluída, pulada, adiada, editada só nela, subtarefas marcadas).

CREATE TABLE task_lists (
    id         UUID PRIMARY KEY,
    user_id    UUID REFERENCES users (id) ON DELETE CASCADE,
    group_id   UUID REFERENCES user_groups (id) ON DELETE CASCADE,
    name       VARCHAR(60) NOT NULL,
    color      VARCHAR(7)  NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((user_id IS NULL) <> (group_id IS NULL))
);

CREATE UNIQUE INDEX uq_task_lists_user_name ON task_lists (user_id, lower(name)) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX uq_task_lists_group_name ON task_lists (group_id, lower(name)) WHERE group_id IS NOT NULL;

CREATE TABLE task_tags (
    id         UUID PRIMARY KEY,
    user_id    UUID REFERENCES users (id) ON DELETE CASCADE,
    group_id   UUID REFERENCES user_groups (id) ON DELETE CASCADE,
    name       VARCHAR(40) NOT NULL,
    color      VARCHAR(7)  NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((user_id IS NULL) <> (group_id IS NULL))
);

CREATE UNIQUE INDEX uq_task_tags_user_name ON task_tags (user_id, lower(name)) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX uq_task_tags_group_name ON task_tags (group_id, lower(name)) WHERE group_id IS NOT NULL;

-- due_date: dia (sem repetição), início da regra ou, em "a partir da conclusão", a próxima ocorrência.
-- repeat_weekdays: bits seg=1 ... dom=64. completed_at: tarefa sem repetição concluída ou série encerrada.
CREATE TABLE tasks (
    id                     UUID PRIMARY KEY,
    user_id                UUID REFERENCES users (id) ON DELETE CASCADE,
    group_id               UUID REFERENCES user_groups (id) ON DELETE CASCADE,
    created_by             UUID REFERENCES users (id) ON DELETE SET NULL,
    list_id                UUID REFERENCES task_lists (id) ON DELETE SET NULL,
    title                  VARCHAR(200) NOT NULL,
    notes                  VARCHAR(5000),
    priority               VARCHAR(6)   NOT NULL DEFAULT 'NONE' CHECK (priority IN ('NONE', 'LOW', 'MEDIUM', 'HIGH')),
    due_date               DATE,
    due_time               TIME,
    duration_minutes       INTEGER CHECK (duration_minutes BETWEEN 1 AND 1440),
    position               INTEGER      NOT NULL,
    habit                  BOOLEAN      NOT NULL DEFAULT FALSE,
    repeat_unit            VARCHAR(5) CHECK (repeat_unit IN ('DAY', 'WEEK', 'MONTH', 'YEAR')),
    repeat_interval        INTEGER CHECK (repeat_interval BETWEEN 1 AND 999),
    repeat_weekdays        SMALLINT CHECK (repeat_weekdays BETWEEN 0 AND 127),
    repeat_month_mode      VARCHAR(12) CHECK (repeat_month_mode IN ('DAY_OF_MONTH', 'LAST_DAY', 'WEEKDAY')),
    repeat_from_completion BOOLEAN      NOT NULL DEFAULT FALSE,
    repeat_until           DATE,
    repeat_count           INTEGER CHECK (repeat_count BETWEEN 1 AND 1000),
    completed_at           TIMESTAMP WITH TIME ZONE,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((user_id IS NULL) <> (group_id IS NULL)),
    CHECK (repeat_unit IS NULL OR (due_date IS NOT NULL AND repeat_interval IS NOT NULL))
);

CREATE INDEX idx_tasks_user ON tasks (user_id, due_date) WHERE user_id IS NOT NULL;
CREATE INDEX idx_tasks_group ON tasks (group_id, due_date) WHERE group_id IS NOT NULL;
CREATE INDEX idx_tasks_list ON tasks (list_id);

CREATE TABLE task_tag_links (
    task_id UUID NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    tag_id  UUID NOT NULL REFERENCES task_tags (id) ON DELETE CASCADE,
    PRIMARY KEY (task_id, tag_id)
);

CREATE INDEX idx_task_tag_links_tag ON task_tag_links (tag_id);

CREATE TABLE task_subtasks (
    id         UUID PRIMARY KEY,
    task_id    UUID         NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    title      VARCHAR(200) NOT NULL,
    position   INTEGER      NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_task_subtasks_task ON task_subtasks (task_id, position);

-- occurrence_date: dia pela regra (sem repetição: due_date; sem data: nulo). moved_to: adiada só esta.
-- title/notes/due_time/priority: editados só nesta ocorrência (nulo = da tarefa).
CREATE TABLE task_occurrences (
    id              UUID PRIMARY KEY,
    task_id         UUID       NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    occurrence_date DATE,
    status          VARCHAR(8) NOT NULL CHECK (status IN ('PENDING', 'DONE', 'SKIPPED')),
    moved_to        DATE,
    title           VARCHAR(200),
    notes           VARCHAR(5000),
    due_time        TIME,
    priority        VARCHAR(6) CHECK (priority IN ('NONE', 'LOW', 'MEDIUM', 'HIGH')),
    completed_at    TIMESTAMP WITH TIME ZONE,
    completed_by    UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE NULLS NOT DISTINCT (task_id, occurrence_date)
);

CREATE INDEX idx_task_occurrences_moved ON task_occurrences (task_id, moved_to) WHERE moved_to IS NOT NULL;
CREATE INDEX idx_task_occurrences_done ON task_occurrences (completed_at DESC) WHERE status = 'DONE';

CREATE TABLE task_subtask_checks (
    occurrence_id UUID NOT NULL REFERENCES task_occurrences (id) ON DELETE CASCADE,
    subtask_id    UUID NOT NULL REFERENCES task_subtasks (id) ON DELETE CASCADE,
    PRIMARY KEY (occurrence_id, subtask_id)
);

CREATE INDEX idx_task_subtask_checks_subtask ON task_subtask_checks (subtask_id);
