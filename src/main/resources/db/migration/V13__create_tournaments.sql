-- Anagrafica competizioni (campionati e coppe) di cui si pronosticano vincitori, capocannoniere ecc.
CREATE TABLE tournaments (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(150) NOT NULL UNIQUE,
    type       VARCHAR(30)  NOT NULL,
    country    VARCHAR(100),
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_tournaments_type ON tournaments (type);
