-- ============================================================
-- V20 — Redesign #3: Calendario per-lega, Concorso Totocalcio, Schedina utente
-- Corregge il modello: la "Giornata" diventa solo calendario per-lega; nasce il
-- Concorso (la schedina giocabile, seleziona partite dello stesso turno tra più leghe);
-- la Schedina utente punta al Concorso. Scommesse: fine campionato (catalogo) +
-- di partita (GiocataPartita). Preserva anagrafiche (leagues, teams, players,
-- seasons, tournaments, rules) e users. Cfr. docs/redesign-3-concorso-totocalcio.md
-- ============================================================

-- 1) Drop tabelle di gioco del redesign #2 (preserva anagrafiche, rules, users)
DROP TABLE IF EXISTS giocate_partita     CASCADE;
DROP TABLE IF EXISTS giocate             CASCADE;
DROP TABLE IF EXISTS schedina_selezioni  CASCADE;
DROP TABLE IF EXISTS schedine            CASCADE;
DROP TABLE IF EXISTS notifications       CASCADE;
DROP TABLE IF EXISTS bet_options         CASCADE;
DROP TABLE IF EXISTS bets                CASCADE;
DROP TABLE IF EXISTS matches             CASCADE;
DROP TABLE IF EXISTS giornate            CASCADE;
DROP TABLE IF EXISTS concorsi            CASCADE;

-- 2) Giornata di campionato (per-lega, solo calendario)
CREATE TABLE giornate (
    id          BIGSERIAL PRIMARY KEY,
    league_id   BIGINT       NOT NULL REFERENCES leagues (id),
    season_id   BIGINT       REFERENCES seasons (id),
    number      INT          NOT NULL,
    name        VARCHAR(150) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 3) Concorso (la schedina giocabile del totocalcio)
CREATE TABLE concorsi (
    id                 BIGSERIAL PRIMARY KEY,
    season_id          BIGINT       REFERENCES seasons (id),
    rule_id            BIGINT       REFERENCES rules (id),
    number             INT          NOT NULL,
    name               VARCHAR(150) NOT NULL,
    open_at            TIMESTAMP    NOT NULL,
    close_at           TIMESTAMP    NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    winning_thresholds TEXT         NOT NULL DEFAULT '[]',
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 4) Partite: sotto la giornata per-lega, selezionabili in un concorso
CREATE TABLE matches (
    id                     BIGSERIAL PRIMARY KEY,
    home_team_id           BIGINT      NOT NULL REFERENCES teams (id),
    away_team_id           BIGINT      NOT NULL REFERENCES teams (id),
    league_id              BIGINT      NOT NULL REFERENCES leagues (id),
    giornata_id            BIGINT      REFERENCES giornate (id),
    concorso_id            BIGINT      REFERENCES concorsi (id),
    first_scorer_player_id BIGINT      REFERENCES players (id),
    scheduled_at           TIMESTAMP   NOT NULL,
    status                 VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    over_under_line        DOUBLE PRECISION NOT NULL DEFAULT 2.5,
    home_score             INT,
    away_score             INT
);

-- 5) Schedina utente (sul concorso) + selezioni 1X2/UO per partita
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
    schedina_id BIGINT     NOT NULL REFERENCES schedine (id) ON DELETE CASCADE,
    match_id    BIGINT     NOT NULL REFERENCES matches (id),
    choice_1x2  VARCHAR(1) NOT NULL,
    choice_uo   VARCHAR(1) NOT NULL,
    correct_1x2 BOOLEAN,
    correct_uo  BOOLEAN
);

-- 6) Scommesse di FINE CAMPIONATO (catalogo) + giocate
CREATE TABLE bets (
    id                  BIGSERIAL PRIMARY KEY,
    label               VARCHAR(200) NOT NULL,
    market              VARCHAR(30)  NOT NULL,
    season_id           BIGINT REFERENCES seasons (id),
    tournament_id       BIGINT REFERENCES tournaments (id),
    league_id           BIGINT REFERENCES leagues (id),
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
CREATE TABLE giocate (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users (id),
    scommessa_id BIGINT      NOT NULL REFERENCES bets (id) ON DELETE CASCADE,
    choice_ref   VARCHAR(50) NOT NULL,
    is_correct   BOOLEAN,
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, scommessa_id)
);

-- 7) Scommesse DI PARTITA (guidate dall'utente)
CREATE TABLE giocate_partita (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    match_id    BIGINT      NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
    market      VARCHAR(30) NOT NULL,
    prediction  VARCHAR(50) NOT NULL,
    is_correct  BOOLEAN,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, match_id, market)
);

-- 8) Notifiche (puntano alle schedine)
CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    schedina_id BIGINT      NOT NULL REFERENCES schedine (id),
    threshold   INT         NOT NULL,
    message     TEXT        NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sent_at     TIMESTAMP,
    read_at     TIMESTAMP,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- 9) Indici
CREATE INDEX idx_giornate_league    ON giornate (league_id);
CREATE INDEX idx_giornate_number    ON giornate (number);
CREATE INDEX idx_matches_giornata   ON matches (giornata_id);
CREATE INDEX idx_matches_concorso   ON matches (concorso_id);
CREATE INDEX idx_concorsi_number    ON concorsi (number);
CREATE INDEX idx_schedine_user      ON schedine (user_id);
CREATE INDEX idx_schedine_concorso  ON schedine (concorso_id);
CREATE INDEX idx_selezioni_schedina ON schedina_selezioni (schedina_id);
CREATE INDEX idx_bets_season        ON bets (season_id);
CREATE INDEX idx_bet_options_bet    ON bet_options (bet_id);
CREATE INDEX idx_giocate_user       ON giocate (user_id);
CREATE INDEX idx_giocate_partita_user  ON giocate_partita (user_id);
CREATE INDEX idx_giocate_partita_match ON giocate_partita (match_id);
CREATE INDEX idx_notif_schedina     ON notifications (schedina_id);
