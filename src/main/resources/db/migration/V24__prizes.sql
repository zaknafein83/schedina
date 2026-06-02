-- Premi per soglia vincente. La Rule porta una mappa soglia->importo (€), uguale per i due
-- giochi (Totocalcio 1X2 e Under/Over). La schedina memorizza il premio vinto per ciascun gioco.
ALTER TABLE rules ADD COLUMN prizes TEXT NOT NULL DEFAULT '{}';
ALTER TABLE schedine ADD COLUMN prize_1x2 BIGINT;
ALTER TABLE schedine ADD COLUMN prize_uo BIGINT;

-- Seed dei premi sulle regole esistenti (soglie standard 9-13): 50k/100k/200k/300k/500k.
UPDATE rules SET prizes = '{"9":50000,"10":100000,"11":200000,"12":300000,"13":500000}'
WHERE prizes = '{}' OR prizes IS NULL;
