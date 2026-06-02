-- Scoring separato per i due giochi della schedina: Totocalcio (1X2) e Under/Over.
-- Prima si calcolava un unico correct_count = (1X2 corretti) + (U/O corretti) con un solo is_winner.
-- Ora si tengono conteggi e vittorie distinti per i due giochi, valutati con le stesse soglie della Regola.
-- correct_count / is_winner restano popolati (somma / vittoria su almeno un gioco) per retrocompatibilità.
ALTER TABLE schedine ADD COLUMN correct_1x2_count INTEGER;
ALTER TABLE schedine ADD COLUMN correct_uo_count INTEGER;
ALTER TABLE schedine ADD COLUMN is_winner_1x2 BOOLEAN;
ALTER TABLE schedine ADD COLUMN is_winner_uo BOOLEAN;
