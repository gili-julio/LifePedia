-- Frequência: compromissos com dias, horário e limite de faltas; opcionalmente ligados a uma tarefa pessoal.
CREATE TABLE commitments (
    id               UUID PRIMARY KEY,
    user_id          UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name             VARCHAR(120)             NOT NULL,
    color            VARCHAR(7)               NOT NULL,
    weekdays         INTEGER[]                NOT NULL CHECK (cardinality(weekdays) BETWEEN 1 AND 7),
    start_time       TIME                     NOT NULL,
    duration_minutes SMALLINT                 NOT NULL CHECK (duration_minutes BETWEEN 5 AND 720),
    start_date       DATE                     NOT NULL,
    end_date         DATE CHECK (end_date >= start_date),
    max_absences     SMALLINT                 NOT NULL CHECK (max_absences BETWEEN 0 AND 999),
    task_id          UUID REFERENCES tasks (id) ON DELETE SET NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_commitments_user ON commitments (user_id);
CREATE INDEX idx_commitments_task ON commitments (task_id);

-- Marcação de um dia (sem linha = pendente, ou presente pela tarefa ligada concluída).
CREATE TABLE commitment_marks (
    id            UUID PRIMARY KEY,
    commitment_id UUID                     NOT NULL REFERENCES commitments (id) ON DELETE CASCADE,
    mark_date     DATE                     NOT NULL,
    status        VARCHAR(8)               NOT NULL CHECK (status IN ('PRESENT', 'ABSENT', 'CANCELED')),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (commitment_id, mark_date)
);
