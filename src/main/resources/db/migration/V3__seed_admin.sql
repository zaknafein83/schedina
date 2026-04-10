-- Utente admin di default
-- Email: admin@schedina.it | Password: admin123
INSERT INTO users (email, username, hashed_password, first_name, last_name, role, is_active)
VALUES (
    'admin@schedina.it',
    'admin',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LWFZcy4pNpO',
    'Admin',
    'Schedina',
    'ADMIN',
    true
);
