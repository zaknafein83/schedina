-- ============================================================
-- Schedina API – schema iniziale
-- ============================================================

CREATE TABLE leagues (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL UNIQUE,
    description TEXT,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    username        VARCHAR(100) UNIQUE,
    hashed_password VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER',
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE teams (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    short_name VARCHAR(10),
    league_id  BIGINT       NOT NULL REFERENCES leagues (id),
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE rules (
    id                       BIGSERIAL PRIMARY KEY,
    name                     VARCHAR(150) NOT NULL,
    description              TEXT,
    league_id                BIGINT       NOT NULL REFERENCES leagues (id),
    required_matches         INT          NOT NULL,
    winning_thresholds       TEXT         NOT NULL DEFAULT '[]',
    max_coupons_per_user     INT,
    max_doubles              INT          NOT NULL DEFAULT 0,
    max_triples              INT          NOT NULL DEFAULT 0,
    full_completion_required BOOLEAN      NOT NULL DEFAULT TRUE,
    is_active                BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE contests (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    league_id   BIGINT       NOT NULL REFERENCES leagues (id),
    rule_id     BIGINT       NOT NULL REFERENCES rules (id),
    open_at     TIMESTAMP    NOT NULL,
    close_at    TIMESTAMP    NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE matches (
    id              BIGSERIAL PRIMARY KEY,
    home_team_id    BIGINT      NOT NULL REFERENCES teams (id),
    away_team_id    BIGINT      NOT NULL REFERENCES teams (id),
    league_id       BIGINT      NOT NULL REFERENCES leagues (id),
    contest_id      BIGINT      REFERENCES contests (id),
    scheduled_at    TIMESTAMP   NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    official_result VARCHAR(5)
);

CREATE TABLE coupons (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users (id),
    contest_id    BIGINT      NOT NULL REFERENCES contests (id),
    created_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    confirmed_at  TIMESTAMP,
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    correct_count INT,
    is_winner     BOOLEAN
);

CREATE TABLE coupon_predictions (
    id         BIGSERIAL PRIMARY KEY,
    coupon_id  BIGINT  NOT NULL REFERENCES coupons (id) ON DELETE CASCADE,
    match_id   BIGINT  NOT NULL REFERENCES matches (id),
    choices    TEXT    NOT NULL,
    is_correct BOOLEAN
);

CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    coupon_id  BIGINT      NOT NULL REFERENCES coupons (id),
    threshold  INT         NOT NULL,
    message    TEXT        NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sent_at    TIMESTAMP,
    read_at    TIMESTAMP,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- Indici
CREATE INDEX idx_users_email        ON users (email);
CREATE INDEX idx_teams_league       ON teams (league_id);
CREATE INDEX idx_matches_contest    ON matches (contest_id);
CREATE INDEX idx_coupons_user       ON coupons (user_id);
CREATE INDEX idx_coupons_contest    ON coupons (contest_id);
CREATE INDEX idx_predictions_coupon ON coupon_predictions (coupon_id);
CREATE INDEX idx_notif_user         ON notifications (user_id);
CREATE INDEX idx_notif_coupon       ON notifications (coupon_id);
