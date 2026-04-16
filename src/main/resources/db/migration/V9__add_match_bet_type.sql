-- Tipo di giocata per singola partita
-- RESULT_1X2    → scelte 1 / X / 2  (default)
-- UNDER_OVER    → scelte U / O, con soglia configurabile (es. 3.5)
ALTER TABLE matches
    ADD COLUMN bet_type       VARCHAR(20)    NOT NULL DEFAULT 'RESULT_1X2',
    ADD COLUMN over_under_line DECIMAL(4,1)  NULL;
