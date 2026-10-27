-- Conquistas: cada nível alcançado fica gravado com a data (conquistado não se perde, mesmo se os dados diminuírem).
CREATE TABLE user_achievements (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code        VARCHAR(30) NOT NULL,
    level       INTEGER     NOT NULL CHECK (level >= 1),
    unlocked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_user_achievements UNIQUE (user_id, code, level)
);
