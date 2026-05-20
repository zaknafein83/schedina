-- Concorsi stagionali (pronostici a fine stagione) e relative schedine

CREATE TABLE season_pools (
    id                  BIGSERIAL PRIMARY KEY,
    season_id           BIGINT       NOT NULL REFERENCES seasons (id),
    name                VARCHAR(150) NOT NULL,
    description         TEXT,
    open_at             TIMESTAMP,
    close_at            TIMESTAMP,
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    winning_thresholds  TEXT         NOT NULL DEFAULT '[]',
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_season_pools_season ON season_pools (season_id);
CREATE INDEX idx_season_pools_status ON season_pools (status);

-- Singolo pronostico configurato nella pool (admin sceglie quali abilitare)
CREATE TABLE season_bets (
    id                  BIGSERIAL PRIMARY KEY,
    season_pool_id      BIGINT       NOT NULL REFERENCES season_pools (id) ON DELETE CASCADE,
    tournament_id       BIGINT       NOT NULL REFERENCES tournaments (id),
    bet_type            VARCHAR(30)  NOT NULL,
    label               VARCHAR(200) NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    official_result_ref VARCHAR(50),
    resolved_at         TIMESTAMP,
    UNIQUE (season_pool_id, tournament_id, bet_type)
);

CREATE INDEX idx_season_bets_pool ON season_bets (season_pool_id);
CREATE INDEX idx_season_bets_tournament ON season_bets (tournament_id);

-- Schedine stagionali
CREATE TABLE season_coupons (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users (id),
    season_pool_id  BIGINT      NOT NULL REFERENCES season_pools (id),
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    correct_count   INT,
    is_winner       BOOLEAN,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    confirmed_at    TIMESTAMP,
    UNIQUE (user_id, season_pool_id)
);

CREATE INDEX idx_season_coupons_user ON season_coupons (user_id);
CREATE INDEX idx_season_coupons_pool ON season_coupons (season_pool_id);

-- Scelte dei pronostici della schedina stagionale
CREATE TABLE season_coupon_predictions (
    id                BIGSERIAL PRIMARY KEY,
    season_coupon_id  BIGINT  NOT NULL REFERENCES season_coupons (id) ON DELETE CASCADE,
    season_bet_id     BIGINT  NOT NULL REFERENCES season_bets (id),
    choice_ref        VARCHAR(50) NOT NULL,
    is_correct        BOOLEAN,
    UNIQUE (season_coupon_id, season_bet_id)
);

CREATE INDEX idx_season_pred_coupon ON season_coupon_predictions (season_coupon_id);
