-- Quarkus BcryptUtil (WildFly Elytron) accetta solo il prefisso $2a$, non $2b$.
-- I due prefissi sono algoritmicamente identici: si aggiorna solo la stringa.
UPDATE users SET hashed_password = replace(hashed_password, '$2b$', '$2a$')
WHERE hashed_password LIKE '$2b$%';
