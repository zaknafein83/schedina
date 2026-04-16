-- Aggiorna password admin a: 12345678
UPDATE users
SET hashed_password = '$2b$10$kMQVEtVw0oar2A7WGSIzq.j5XXoPZohy.ofmgwEDGG0FOse1QDDRO'
WHERE email = 'admin@schedina.it';
