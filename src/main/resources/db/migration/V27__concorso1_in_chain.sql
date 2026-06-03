-- Decisione rivista: anche il concorso #1 entra nella CATENA del montepremi (start 500k),
-- così i concorsi successivi partono dal montepremi calcolato dai suoi esiti.
-- I premi di #1 verranno ricalcolati dalla formula alla prossima elaborazione (recompute).
UPDATE concorsi SET montepremi_managed = TRUE WHERE montepremi_managed = FALSE;
