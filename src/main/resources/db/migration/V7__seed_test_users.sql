-- Utenti di test per ambiente locale
-- mod@schedina.it     / mod1234
-- mario@schedina.it   / user1234
-- giulia@schedina.it  / user1234

INSERT INTO users (email, username, hashed_password, first_name, last_name, role, is_active) VALUES
    ('mod@schedina.it',    'mod',    '$2b$10$L1Q5HwCyfdiqkQ8a8g/UoeJaUbKG7lbrExhqioojNE8GKerV7oo6i', 'Marco',  'Moderatore', 'MOD',  true),
    ('mario@schedina.it',  'mario',  '$2b$10$fN9kcl4Ji7gLMI1eVzvPeupfHWThF7gl3WMzK0sHakx0Z.76Lst8e', 'Mario',  'Rossi',      'USER', true),
    ('giulia@schedina.it', 'giulia', '$2b$10$gDy9Bg.2oGDVVH4h50C2ReFt8H0WQHYyycirZaA6q/TzAKpOa76mq', 'Giulia', 'Bianchi',    'USER', true);
