-- Escola: vínculos (graduação, mestrado...), matérias por semestre com notas e links, provas e trabalhos.
CREATE TABLE school_enrollments (
    id                     UUID PRIMARY KEY,
    user_id                UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind                   VARCHAR(20)              NOT NULL,
    course                 VARCHAR(160)             NOT NULL,
    institution            VARCHAR(160),
    registration           VARCHAR(40),
    start_period           VARCHAR(7),
    active                 BOOLEAN                  NOT NULL,
    color                  VARCHAR(7)               NOT NULL,
    scale                  VARCHAR(10)              NOT NULL,
    pass_average           NUMERIC(4, 2)            NOT NULL,
    low_average            NUMERIC(4, 2)            NOT NULL,
    min_unit               NUMERIC(4, 2)            NOT NULL,
    min_attendance         INT                      NOT NULL CHECK (min_attendance BETWEEN 0 AND 100),
    required_mandatory     INT CHECK (required_mandatory >= 0),
    required_optional      INT CHECK (required_optional >= 0),
    required_complementary INT CHECK (required_complementary >= 0),
    complementary_done     INT                      NOT NULL DEFAULT 0 CHECK (complementary_done >= 0),
    position               INT                      NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_school_enrollments_user ON school_enrollments (user_id, position);

-- Matéria de um semestre (período nulo = aproveitada sem semestre, ex.: CUMP do SIGAA). Notas das unidades em JSON.
CREATE TABLE school_courses (
    id             UUID PRIMARY KEY,
    user_id        UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    enrollment_id  UUID                     NOT NULL REFERENCES school_enrollments (id) ON DELETE CASCADE,
    period         VARCHAR(7),
    code           VARCHAR(20),
    name           VARCHAR(200)             NOT NULL,
    hours          INT      CHECK (hours BETWEEN 0 AND 2000),
    class_code     VARCHAR(20),
    category       VARCHAR(14)              NOT NULL,
    status         VARCHAR(6)               NOT NULL,
    attendance     NUMERIC(5, 2),
    average        NUMERIC(4, 2),
    concept        VARCHAR(2),
    units          TEXT                     NOT NULL CHECK (length(units) <= 2000),
    recovery       NUMERIC(4, 2),
    absences       INT,
    teacher        VARCHAR(160),
    teacher_email  VARCHAR(160),
    room           VARCHAR(80),
    schedule       VARCHAR(80),
    links          TEXT                     NOT NULL CHECK (length(links) <= 8000),
    commitment_id  UUID REFERENCES commitments (id) ON DELETE SET NULL,
    note_folder_id UUID REFERENCES note_folders (id) ON DELETE SET NULL,
    group_id       UUID REFERENCES user_groups (id) ON DELETE SET NULL,
    risk_warned    VARCHAR(20),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_school_courses_enrollment ON school_courses (enrollment_id, period);
CREATE INDEX idx_school_courses_user ON school_courses (user_id);
CREATE INDEX idx_school_courses_group ON school_courses (group_id);

-- Prova ou trabalho de uma matéria; a nota entra na unidade e a data pode virar tarefa.
CREATE TABLE school_assessments (
    id          UUID PRIMARY KEY,
    course_id   UUID                     NOT NULL REFERENCES school_courses (id) ON DELETE CASCADE,
    user_id     UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title       VARCHAR(120)             NOT NULL,
    kind        VARCHAR(12)              NOT NULL,
    due_date    DATE,
    due_time    TIME,
    unit        INT      CHECK (unit BETWEEN 1 AND 8),
    weight      NUMERIC(5, 2)            NOT NULL CHECK (weight > 0),
    grade       NUMERIC(4, 2),
    task_id     UUID REFERENCES tasks (id) ON DELETE SET NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_school_assessments_course ON school_assessments (course_id, due_date);
