-- Seed iniziale: stagione 2025-26 (corrente) + tornei base.
-- Idempotente: usa ON CONFLICT DO NOTHING su tutti gli inserimenti.

-- Stagione corrente
INSERT INTO seasons (label, start_date, end_date, is_current)
VALUES ('2025-26', '2025-08-01', '2026-06-30', TRUE)
ON CONFLICT (label) DO NOTHING;

-- Tornei base
INSERT INTO tournaments (name, type, country, is_active) VALUES
    ('Serie A',           'LEAGUE_NATIONAL',   'Italia', TRUE),
    ('Serie B',           'LEAGUE_NATIONAL',   'Italia', TRUE),
    ('Serie C',           'LEAGUE_NATIONAL',   'Italia', TRUE),
    ('Coppa Italia',      'CUP_NATIONAL',      'Italia', TRUE),
    ('Champions League',  'CUP_INTERNATIONAL', NULL,     TRUE)
ON CONFLICT (name) DO NOTHING;
