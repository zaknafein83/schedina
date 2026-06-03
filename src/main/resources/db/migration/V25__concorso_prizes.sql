-- Premi spostati dalla Regola al Concorso, SEPARATI per gioco (Totocalcio 1X2 e Under/Over),
-- così ogni concorso può mettere in palio importi diversi e diversi tra i due giochi.
-- Additiva e NON distruttiva: la colonna rules.prizes resta (deprecata) per non perdere dati.
ALTER TABLE concorsi ADD COLUMN prizes_1x2 TEXT NOT NULL DEFAULT '{}';
ALTER TABLE concorsi ADD COLUMN prizes_uo  TEXT NOT NULL DEFAULT '{}';

-- Copia i premi attuali della regola in ENTRAMBI i giochi del concorso (preserva il comportamento esistente).
UPDATE concorsi c SET prizes_1x2 = r.prizes, prizes_uo = r.prizes
FROM rules r
WHERE c.rule_id = r.id AND r.prizes IS NOT NULL AND r.prizes <> '{}';
