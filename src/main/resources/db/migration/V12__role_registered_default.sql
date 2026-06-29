-- Nuovo stato iniziale alla registrazione: ROLE_REGISTERED (nessun accesso al riassunto IA).
-- Cambia solo il DEFAULT per i futuri insert: gli utenti esistenti mantengono il ruolo attuale
-- (chi era ROLE_USER resta abilitato). L'abilitazione/promozione si fa a mano:
--   UPDATE users SET role = 'ROLE_USER'  WHERE github_username = '...';
--   UPDATE users SET role = 'ROLE_ADMIN' WHERE github_username = '...';
ALTER TABLE users
    ALTER COLUMN role SET DEFAULT 'ROLE_REGISTERED';
