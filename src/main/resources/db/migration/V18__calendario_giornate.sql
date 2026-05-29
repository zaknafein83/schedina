-- ============================================================
-- V18 — Calendario / Giornate
-- Separa la Schedina (1X2 + U/O per giornata) dalle Scommesse extra (fine
-- stagione / di giornata, con Giocate indipendenti). Sostituisce Concorso con
-- Giornata. Preserva anagrafiche (leagues, teams, players, seasons, tournaments)
-- e users. Cfr. docs/redesign-calendario-giornate.md
-- ============================================================

-- 1) Drop tabelle di gioco del redesign #1
DROP TABLE IF EXISTS schedina_selezioni CASCADE;
DROP TABLE IF EXISTS schedine           CASCADE;
DROP TABLE IF EXISTS bet_options        CASCADE;
DROP TABLE IF EXISTS bets               CASCADE;
DROP TABLE IF EXISTS notifications      CASCADE;
DROP TABLE IF EXISTS concorsi           CASCADE;
DROP TABLE IF EXISTS rules              CASCADE;

-- 2) matches: ripuliti (erano dati di test) e arricchiti con giornata + soglia U/O
TRUNCATE TABLE matches RESTART IDENTITY CASCADE;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS giornata_id     BIGINT;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS over_under_line DOUBLE PRECISION NOT NULL DEFAULT 2.5;

-- 3) Calendario
CREATE TABLE giornate (
    id                 BIGSERIAL PRIMARY KEY,
    season_id          BIGINT REFERENCES seasons (id),
    number             INT          NOT NULL,
    name               VARCHAR(150) NOT NULL,
    open_at            TIMESTAMP    NOT NULL,
    close_at           TIMESTAMP    NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    winning_thresholds TEXT         NOT NULL DEFAULT '[]',
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);
ALTER TABLE matches ADD CONSTRAINT fk_matches_giornata FOREIGN KEY (giornata_id) REFERENCES giornate (id);

-- 4) Schedina (1X2 + U/O per ogni partita della giornata)
CREATE TABLE schedine (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users (id),
    giornata_id   BIGINT      NOT NULL REFERENCES giornate (id),
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

-- 5) Scommesse extra + giocate
CREATE TABLE bets (
    id                  BIGSERIAL PRIMARY KEY,
    scope               VARCHAR(20)  NOT NULL DEFAULT 'SEASON',
    label               VARCHAR(200) NOT NULL,
    market              VARCHAR(30)  NOT NULL,
    season_id           BIGINT REFERENCES seasons (id),
    giornata_id         BIGINT REFERENCES giornate (id),
    match_id            BIGINT REFERENCES matches (id),
    tournament_id       BIGINT REFERENCES tournaments (id),
    league_id           BIGINT REFERENCES leagues (id),
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
CREATE TABLE giocate (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users (id),
    scommessa_id BIGINT      NOT NULL REFERENCES bets (id) ON DELETE CASCADE,
    choice_ref   VARCHAR(50) NOT NULL,
    is_correct   BOOLEAN,
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, scommessa_id)
);

-- 6) notifications (ricreata, punta alle schedine)
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

-- 7) Indici
CREATE INDEX idx_matches_giornata   ON matches (giornata_id);
CREATE INDEX idx_giornate_season    ON giornate (season_id);
CREATE INDEX idx_schedine_user      ON schedine (user_id);
CREATE INDEX idx_schedine_giornata  ON schedine (giornata_id);
CREATE INDEX idx_selezioni_schedina ON schedina_selezioni (schedina_id);
CREATE INDEX idx_bets_giornata      ON bets (giornata_id);
CREATE INDEX idx_bets_season        ON bets (season_id);
CREATE INDEX idx_bet_options_bet    ON bet_options (bet_id);
CREATE INDEX idx_giocate_user       ON giocate (user_id);
CREATE INDEX idx_giocate_scommessa  ON giocate (scommessa_id);
CREATE INDEX idx_notif_schedina     ON notifications (schedina_id);
