-- Notifiche per-gioco: dopo lo split della schedina (V22) una notifica si riferisce a
-- UNO dei due giochi vinti (Totocalcio 1X2 oppure Under/Over), non più al punteggio combinato.
-- Colonna nullable: le righe legacy (notifiche del vecchio modello combinato) restano con game=NULL.
ALTER TABLE notifications ADD COLUMN game VARCHAR(20);
