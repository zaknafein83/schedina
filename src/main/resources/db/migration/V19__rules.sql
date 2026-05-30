-- Reintroduce le Regole riusabili (soglie vincenti della schedina) e le collega alle giornate.
-- Additiva: non tocca i dati di gioco esistenti. La giornata può restare senza regola
-- (in tal caso lo scoring usa il campo legacy giornate.winning_thresholds come fallback).

CREATE TABLE rules (
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(150) NOT NULL,
    winning_thresholds TEXT         NOT NULL DEFAULT '[]',
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

ALTER TABLE giornate ADD COLUMN rule_id BIGINT REFERENCES rules (id);
