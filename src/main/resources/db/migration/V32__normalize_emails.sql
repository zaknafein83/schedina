-- V32: normalizza le email esistenti (trim + minuscolo) così che login e reset
-- password diventino case-insensitive in modo retroattivo e trasparente per gli utenti.
--
-- Salta gli account che, una volta normalizzati, collidono con un altro account già
-- esistente: sono doppioni storici nati dalla ri-registrazione di utenti che non
-- riuscivano ad accedere. Quelli NON vengono toccati qui (violerebbero il vincolo di
-- unicità e/o farebbero perdere dati di gioco) e vanno riconciliati a mano.
UPDATE users u
SET email = lower(btrim(u.email))
WHERE u.email <> lower(btrim(u.email))
  AND NOT EXISTS (
      SELECT 1 FROM users u2
      WHERE u2.id <> u.id
        AND u2.email = lower(btrim(u.email))
  );
