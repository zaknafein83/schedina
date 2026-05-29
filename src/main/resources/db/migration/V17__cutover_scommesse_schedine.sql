-- ============================================================
-- V17 — Cutover redesign Scommesse / Schedine
-- Sostituisce lo schema di gioco (concorsi a giornata + stagionali) con il modello
-- unificato Scommessa/Schedina. Preserva utenti e anagrafiche (leagues, teams, players,
-- seasons, tournaments). Cutover pulito: i dati di gioco erano di test.
-- Cfr. docs/redesign-scommesse-schedine.md
-- ============================================================

-- 1) Drop tabelle di gioco obsolete (CASCADE rimuove anche i vincoli FK dipendenti)
DROP TABLE IF EXISTS coupon_side_predictions    CASCADE;
DROP TABLE IF EXISTS coupon_predictions         CASCADE;
DROP TABLE IF EXISTS season_coupon_predictions  CASCADE;
DROP TABLE IF EXISTS season_coupons             CASCADE;
DROP TABLE IF EXISTS season_bets                CASCADE;
DROP TABLE IF EXISTS season_pools               CASCADE;
DROP TABLE IF EXISTS match_side_predictions     CASCADE;
DROP TABLE IF EXISTS coupons                     CASCADE;
DROP TABLE IF EXISTS contests                    CASCADE;

-- 2) matches: resta il solo fixture con punteggio (il legame col concorso passa da bets)
DROP INDEX IF EXISTS idx_matches_contest;
ALTER TABLE matches DROP COLUMN IF EXISTS contest_id;
ALTER TABLE matches DROP COLUMN IF EXISTS bet_type;
ALTER TABLE matches DROP COLUMN IF EXISTS over_under_line;
ALTER TABLE matches DROP COLUMN IF EXISTS official_result;

-- 3) rules: snellita (via doppie/triple), rinomini, lega opzionale
ALTER TABLE rules DROP COLUMN IF EXISTS max_doubles;
ALTER TABLE rules DROP COLUMN IF EXISTS max_triples;
ALTER TABLE rules RENAME COLUMN required_matches     TO required_bets;
ALTER TABLE rules RENAME COLUMN max_coupons_per_user TO max_schedine_per_user;
ALTER TABLE rules ALTER COLUMN league_id DROP NOT NULL;

-- 4) Nuovo schema
CREATE TABLE concorsi (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    kind        VARCHAR(20)  NOT NULL DEFAULT 'MATCHDAY',
    rule_id     BIGINT       NOT NULL REFERENCES rules (id),
    open_at     TIMESTAMP    NOT NULL,
    close_at    TIMESTAMP    NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE bets (
    id                  BIGSERIAL PRIMARY KEY,
    concorso_id         BIGINT       REFERENCES concorsi (id) ON DELETE CASCADE,
    label               VARCHAR(200) NOT NULL,
    market              VARCHAR(30)  NOT NULL,
    match_id            BIGINT       REFERENCES matches (id),
    tournament_id       BIGINT       REFERENCES tournaments (id),
    season_id           BIGINT       REFERENCES seasons (id),
    league_id           BIGINT       REFERENCES leagues (id),
    over_under_line     DOUBLE PRECISION,
    resolution_mode     VARCHAR(10)  NOT NULL DEFAULT 'MANUAL',
    status              VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    official_result_ref VARCHAR(50),
    resolved_at         TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE bet_options (
    id            BIGSERIAL PRIMARY KEY,
    bet_id        BIGINT       NOT NULL REFERENCES bets (id) ON DELETE CASCADE,
    ref           VARCHAR(50)  NOT NULL,
    label         VARCHAR(200) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0
);

CREATE TABLE schedine (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users (id),
    concorso_id   BIGINT      NOT NULL REFERENCES concorsi (id),
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    correct_count INT,
    is_winner     BOOLEAN,
    confirmed_at  TIMESTAMP,
    created_at    TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE schedina_selezioni (
    id          BIGSERIAL PRIMARY KEY,
    schedina_id BIGINT      NOT NULL REFERENCES schedine (id) ON DELETE CASCADE,
    bet_id      BIGINT      NOT NULL REFERENCES bets (id),
    choice_ref  VARCHAR(50) NOT NULL,
    is_correct  BOOLEAN
);

-- 5) notifications: ora puntano alle schedine
DROP INDEX IF EXISTS idx_notif_coupon;
ALTER TABLE notifications RENAME COLUMN coupon_id TO schedina_id;
ALTER TABLE notifications
    ADD CONSTRAINT fk_notif_schedina FOREIGN KEY (schedina_id) REFERENCES schedine (id);

-- 6) Indici
CREATE INDEX idx_bets_concorso      ON bets (concorso_id);
CREATE INDEX idx_bets_match         ON bets (match_id);
CREATE INDEX idx_bet_options_bet    ON bet_options (bet_id);
CREATE INDEX idx_schedine_user      ON schedine (user_id);
CREATE INDEX idx_schedine_concorso  ON schedine (concorso_id);
CREATE INDEX idx_selezioni_schedina ON schedina_selezioni (schedina_id);
CREATE INDEX idx_notif_schedina     ON notifications (schedina_id);
