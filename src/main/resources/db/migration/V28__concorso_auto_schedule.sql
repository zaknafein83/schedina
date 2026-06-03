-- Apertura/chiusura automatica dei concorsi del Totocalcio.
-- open_at diventa nullable: il primo concorso (senza turno precedente) non ha un'apertura
-- automatica calcolata → resta in bozza e lo apre l'admin.
ALTER TABLE concorsi ALTER COLUMN open_at DROP NOT NULL;

-- close_auto: la chiusura automatica (20:30 del giorno delle partite) è attiva di default;
-- una riapertura manuale dell'admin la disabilita, così lo scheduler non richiude il concorso.
ALTER TABLE concorsi ADD COLUMN close_auto BOOLEAN NOT NULL DEFAULT TRUE;
