-- Crea il primo concorso 1X2 con partite casuali tratte dall'unica lega esistente.
-- Lavora su qualsiasi numero di squadre attive: n_partite = LEAST(floor(n/2), 13).
-- Se non esiste una regola con required_matches corrispondente, ne crea una al volo.

DO $$
DECLARE
    v_league_id   BIGINT;
    v_rule_id     BIGINT;
    v_contest_id  BIGINT;
    v_team_ids    BIGINT[];
    v_n_teams     INT;
    v_n_matches   INT;
    i             INT;
BEGIN
    -- Prende la prima (e unica) lega
    SELECT id INTO v_league_id FROM leagues ORDER BY id LIMIT 1;
    IF v_league_id IS NULL THEN
        RAISE NOTICE 'V10 skip: nessuna lega presente, seed concorso non eseguito';
        RETURN;
    END IF;

    -- Squadre attive della lega in ordine casuale
    SELECT ARRAY(
        SELECT id FROM teams
        WHERE league_id = v_league_id AND is_active = TRUE
        ORDER BY RANDOM()
    ) INTO v_team_ids;

    v_n_teams   := COALESCE(array_length(v_team_ids, 1), 0);
    v_n_matches := LEAST(v_n_teams / 2, 13);

    IF v_n_matches < 1 THEN
        RAISE NOTICE 'V10 skip: squadre insufficienti (trovate: %), seed concorso non eseguito', v_n_teams;
        RETURN;
    END IF;

    -- Cerca regola esistente compatibile (stessa lega, stesso numero partite)
    SELECT id INTO v_rule_id
    FROM rules
    WHERE league_id = v_league_id
      AND required_matches = v_n_matches
      AND is_active = TRUE
    LIMIT 1;

    -- Se non esiste, la crea
    IF v_rule_id IS NULL THEN
        INSERT INTO rules (name, description, league_id, required_matches,
                           winning_thresholds, max_coupons_per_user,
                           max_doubles, max_triples, full_completion_required, is_active)
        VALUES (
            'Regola ' || v_n_matches || ' partite',
            'Regola generata automaticamente – ' || v_n_matches || ' pronostici obbligatori',
            v_league_id,
            v_n_matches,
            '[' || v_n_matches || ']',   -- vince solo chi indovina tutte
            3,                            -- max 3 schedine per utente
            3,                            -- max 3 doppie
            1,                            -- max 1 tripla
            TRUE,
            TRUE
        )
        RETURNING id INTO v_rule_id;
        RAISE NOTICE 'Creata nuova regola id=% con % partite richieste', v_rule_id, v_n_matches;
    END IF;

    -- Crea il concorso aperto
    INSERT INTO contests (name, description, league_id, rule_id,
                          open_at, close_at, status)
    VALUES (
        'Schedina #1',
        'Primo concorso – ' || v_n_matches || ' partite 1X2',
        v_league_id,
        v_rule_id,
        NOW() - INTERVAL '1 hour',          -- già aperto
        NOW() + INTERVAL '6 days 23 hours', -- chiude tra ~7 giorni
        'OPEN'
    )
    RETURNING id INTO v_contest_id;

    -- Crea le partite accoppiando le squadre due a due
    FOR i IN 1..v_n_matches LOOP
        INSERT INTO matches (home_team_id, away_team_id, league_id, contest_id,
                             scheduled_at, status, bet_type)
        VALUES (
            v_team_ids[i * 2 - 1],
            v_team_ids[i * 2],
            v_league_id,
            v_contest_id,
            NOW() + (i || ' days')::INTERVAL,
            'OPEN',
            'RESULT_1X2'
        );
    END LOOP;

    RAISE NOTICE 'Concorso #% creato con % partite (regola id=%)', v_contest_id, v_n_matches, v_rule_id;
END $$;
