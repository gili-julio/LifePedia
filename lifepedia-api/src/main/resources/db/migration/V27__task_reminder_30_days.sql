-- Lembrete de tarefa até 30 dias antes (antes: até 1 dia).
ALTER TABLE tasks DROP CONSTRAINT tasks_reminder_minutes_check;
ALTER TABLE tasks ADD CONSTRAINT ck_tasks_reminder_minutes CHECK (reminder_minutes BETWEEN 0 AND 43200);
