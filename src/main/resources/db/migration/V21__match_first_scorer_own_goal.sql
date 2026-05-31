-- Autogol come "primo marcatore": flag dedicato perché first_scorer_player_id ha una FK a players
-- e non può rappresentare l'autogol (nessun giocatore).
ALTER TABLE matches ADD COLUMN first_scorer_own_goal BOOLEAN NOT NULL DEFAULT FALSE;
