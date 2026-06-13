-- Aggiunge la Salernitana alle squadre deprioritizzate nell'autocompletamento del concorso
-- (vedi V30). No-op se la squadra non esiste ancora: la si potrà marcare dalla gestione Squadre.
UPDATE teams SET autofill_excluded = TRUE WHERE name = 'Salernitana';
