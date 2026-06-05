-- Squadre da deprioritizzare nell'autocompletamento del concorso: l'autofill preferisce le
-- combinazioni delle altre squadre e usa queste solo se necessario a raggiungere la quota.
ALTER TABLE teams ADD COLUMN autofill_excluded BOOLEAN NOT NULL DEFAULT FALSE;

-- Seed iniziale (richiesta utente). "Ternana" potrebbe non esistere ancora: in tal caso è un no-op
-- e la si potrà marcare dalla gestione Squadre quando verrà creata.
UPDATE teams SET autofill_excluded = TRUE
WHERE name IN ('Ascoli', 'Atalanta', 'Foggia', 'Hellas Verona', 'Lecce', 'Perugia', 'Ternana');
