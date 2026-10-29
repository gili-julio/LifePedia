-- Horas de sono por dia da semana (minutos, seg..dom), descontadas nos indicadores da agenda.
ALTER TABLE agenda_settings
    ADD COLUMN sleep_minutes INTEGER[] NOT NULL DEFAULT '{480,480,480,480,480,480,480}'
        CHECK (cardinality(sleep_minutes) = 7 AND 0 <= ALL (sleep_minutes) AND 960 >= ALL (sleep_minutes));
