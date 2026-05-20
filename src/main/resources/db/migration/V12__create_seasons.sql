-- Anagrafica stagioni sportive (es. "2025-26")
CREATE TABLE seasons (
    id          BIGSERIAL PRIMARY KEY,
    label       VARCHAR(50) NOT NULL UNIQUE,
    start_date  DATE,
    end_date    DATE,
    is_current  BOOLEAN     NOT NULL DEFAULT FALSE
);

-- Vincolo: al massimo una stagione con is_current = TRUE
CREATE UNIQUE INDEX ux_seasons_only_one_current
    ON seasons (is_current) WHERE is_current = TRUE;
