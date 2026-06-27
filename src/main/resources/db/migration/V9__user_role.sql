-- Ruolo applicativo dell'utente per il controllo accessi (es. pannello di amministrazione).
-- Default ROLE_USER: ogni nuovo iscritto è utente normale; gli admin si promuovono a mano via DB
-- (UPDATE users SET role = 'ROLE_ADMIN' WHERE github_username = '...').
ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER';
