-- Aggiunge i campi punteggio alla tabella matches
ALTER TABLE matches
    ADD COLUMN home_score INTEGER,
    ADD COLUMN away_score INTEGER;
