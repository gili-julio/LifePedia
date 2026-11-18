-- Entregas: transportadora escolhida, passos lançados à mão e a situação vinda só do rastreio.
-- status passa a ser a situação mostrada: a mais avançada entre o rastreio (tracked_status) e os passos manuais.
ALTER TABLE deliveries
    ADD COLUMN tracked_status    VARCHAR(20),
    ADD COLUMN manual_events     TEXT CHECK (length(manual_events) <= 20000),
    ADD COLUMN carrier_choice    INTEGER,
    ADD COLUMN carrier_requested INTEGER;

UPDATE deliveries SET tracked_status = status;

ALTER TABLE deliveries ALTER COLUMN tracked_status SET NOT NULL;
