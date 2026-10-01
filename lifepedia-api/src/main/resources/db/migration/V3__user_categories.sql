-- Categorias passam a ser do usuário (editáveis, com cor), em vez de um enum fixo no código.

CREATE TABLE categories (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(60) NOT NULL,
    type       VARCHAR(10) NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    color      VARCHAR(7)  NOT NULL CHECK (color ~ '^#[0-9a-f]{6}$'),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_categories_user_type_name ON categories (user_id, type, lower(name));

-- Categorias padrão para os usuários que já existem. Novos usuários recebem as mesmas via
-- com.lifepedia.api.finance.category.DefaultCategories (mantenha as duas listas iguais).
-- "code" é o valor do antigo enum, usado só para migrar os lançamentos existentes.
CREATE TEMPORARY TABLE default_categories (code VARCHAR(30), name VARCHAR(60), type VARCHAR(10), color VARCHAR(7))
    ON COMMIT DROP;

INSERT INTO default_categories (code, name, type, color) VALUES
    ('HOUSING',       'Moradia',            'EXPENSE', '#2a78d6'),
    ('UTILITIES',     'Contas da casa',     'EXPENSE', '#1baf7a'),
    ('FOOD',          'Alimentação',        'EXPENSE', '#eb6834'),
    ('TRANSPORT',     'Transporte',         'EXPENSE', '#eda100'),
    ('HEALTH',        'Saúde',              'EXPENSE', '#e34948'),
    ('EDUCATION',     'Educação',           'EXPENSE', '#4a3aa7'),
    ('LEISURE',       'Lazer',              'EXPENSE', '#e87ba4'),
    ('SUBSCRIPTIONS', 'Assinaturas',        'EXPENSE', '#9b51c9'),
    ('SHOPPING',      'Compras',            'EXPENSE', '#0e9aa7'),
    ('TAXES',         'Impostos e taxas',   'EXPENSE', '#9c6b3c'),
    ('DEBTS',         'Dívidas e parcelas', 'EXPENSE', '#a61e4d'),
    ('GIFTS',         'Presentes',          'EXPENSE', '#008300'),
    ('OTHER',         'Outros',             'EXPENSE', '#64748b'),
    ('SALARY',        'Salário',            'INCOME',  '#2a78d6'),
    ('FREELANCE',     'Freelance / extra',  'INCOME',  '#1baf7a'),
    ('INVESTMENTS',   'Investimentos',      'INCOME',  '#008300'),
    ('BENEFITS',      'Benefícios',         'INCOME',  '#eda100'),
    ('GIFTS',         'Presentes',          'INCOME',  '#e87ba4'),
    ('OTHER',         'Outros',             'INCOME',  '#64748b');

CREATE TEMPORARY TABLE category_map ON COMMIT DROP AS
SELECT gen_random_uuid() AS id, u.id AS user_id, d.code, d.name, d.type, d.color
FROM users u
CROSS JOIN default_categories d;

INSERT INTO categories (id, user_id, name, type, color, created_at, updated_at)
SELECT id, user_id, name, type, color, now(), now()
FROM category_map;

-- Lançamentos fixos: enum -> category_id
ALTER TABLE recurring_entries ADD COLUMN category_id UUID REFERENCES categories (id);

UPDATE recurring_entries r
SET category_id = m.id
FROM category_map m
WHERE m.user_id = r.user_id AND m.type = r.type AND m.code = r.category;

ALTER TABLE recurring_entries ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE recurring_entries DROP COLUMN category;
CREATE INDEX idx_recurring_entries_category ON recurring_entries (category_id);

-- Lançamentos ocasionais: enum -> category_id
ALTER TABLE transactions ADD COLUMN category_id UUID REFERENCES categories (id);

UPDATE transactions t
SET category_id = m.id
FROM category_map m
WHERE m.user_id = t.user_id AND m.type = t.type AND m.code = t.category;

ALTER TABLE transactions ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE transactions DROP COLUMN category;
CREATE INDEX idx_transactions_category ON transactions (category_id);
