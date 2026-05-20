-- Pronostici "extra" per singola partita (gol/no gol, primo marcatore)

CREATE TABLE match_side_predictions (
    id              BIGSERIAL PRIMARY KEY,
    match_id        BIGINT       NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
    bet_type        VARCHAR(30)  NOT NULL,
    label           VARCHAR(200) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    official_result VARCHAR(50),
    resolved_at     TIMESTAMP,
    UNIQUE (match_id, bet_type)
);

CREATE INDEX idx_msp_match ON match_side_predictions (match_id);

-- Scelte dell'utente per i side bet della propria schedina
CREATE TABLE coupon_side_predictions (
    id                       BIGSERIAL PRIMARY KEY,
    coupon_id                BIGINT  NOT NULL REFERENCES coupons (id) ON DELETE CASCADE,
    match_side_prediction_id BIGINT  NOT NULL REFERENCES match_side_predictions (id),
    choice                   VARCHAR(50) NOT NULL,
    is_correct               BOOLEAN,
    UNIQUE (coupon_id, match_side_prediction_id)
);

CREATE INDEX idx_csp_coupon ON coupon_side_predictions (coupon_id);
