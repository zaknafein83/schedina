-- Anagrafica giocatori (utilizzata per pronostici tipo Capocannoniere, Miglior portiere, Primo marcatore)
CREATE TABLE players (
    id          BIGSERIAL PRIMARY KEY,
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    team_id     BIGINT       REFERENCES teams (id) ON DELETE SET NULL,
    role        VARCHAR(10),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_players_team ON players (team_id);
CREATE INDEX idx_players_name ON players (last_name, first_name);
