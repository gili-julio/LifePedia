-- Tarefas: lembrete (minutos antes do horário; 0 = na hora) e ligação com um fixo do financeiro (só pessoais).

ALTER TABLE tasks ADD COLUMN reminder_minutes INTEGER CHECK (reminder_minutes BETWEEN 0 AND 1440);
ALTER TABLE tasks ADD COLUMN recurring_entry_id UUID REFERENCES recurring_entries (id) ON DELETE SET NULL;
ALTER TABLE tasks ADD CONSTRAINT ck_tasks_reminder_time CHECK (reminder_minutes IS NULL OR due_time IS NOT NULL);
ALTER TABLE tasks ADD CONSTRAINT ck_tasks_recurring_personal CHECK (recurring_entry_id IS NULL OR user_id IS NOT NULL);

CREATE INDEX idx_tasks_reminder ON tasks (due_date) WHERE reminder_minutes IS NOT NULL AND completed_at IS NULL;
CREATE INDEX idx_tasks_recurring_entry ON tasks (recurring_entry_id) WHERE recurring_entry_id IS NOT NULL;
