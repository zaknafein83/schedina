-- Montepremi per gioco (Totocalcio 1X2 / Under-Over): stato che si trascina di concorso in
-- concorso. I premi non sono più inseriti a mano ma CALCOLATI dal montepremi (vedi MontepremiService).
-- montepremi_1x2 / montepremi_uo = montepremi IN INGRESSO usato dal concorso (snapshot, per audit/UI).
-- montepremi_managed = false per i concorsi "legacy" (es. il #1) che tengono i premi attuali e
-- restano FUORI dalla catena del montepremi.
ALTER TABLE concorsi ADD COLUMN montepremi_1x2 BIGINT;
ALTER TABLE concorsi ADD COLUMN montepremi_uo  BIGINT;
ALTER TABLE concorsi ADD COLUMN montepremi_managed BOOLEAN NOT NULL DEFAULT TRUE;

-- I concorsi già esistenti (es. #1) restano legacy: tengono i premi attuali, fuori dalla catena.
UPDATE concorsi SET montepremi_managed = FALSE;
