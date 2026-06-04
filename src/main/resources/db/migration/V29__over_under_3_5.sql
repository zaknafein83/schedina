-- La soglia Under/Over standard passa da 2.5 a 3.5.
-- Nuove partite: default 3.5.
ALTER TABLE matches ALTER COLUMN over_under_line SET DEFAULT 3.5;

-- Partite esistenti ancora alla vecchia soglia standard 2.5 → portate a 3.5
-- (concorsi passati e correnti; eventuali soglie personalizzate diverse da 2.5 restano intatte).
-- NB OPERATIVO: dopo questa migrazione i concorsi GIÀ ELABORATI vanno RIELABORATI
-- (POST /admin/concorsi/{id}/process) per ricalcolare esiti U/O, vincitori, premi,
-- montepremi (catena) e notifiche sulla nuova soglia.
UPDATE matches SET over_under_line = 3.5 WHERE over_under_line = 2.5;
