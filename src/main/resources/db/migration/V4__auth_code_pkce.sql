-- PKCE (RFC 7636) sul leg app <-> backend: leghiamo al codice usa-e-getta la "code challenge"
-- (BASE64URL(SHA-256(code_verifier)), 43 caratteri per il metodo S256) ricevuta all'avvio del login.
-- All'exchange l'app presenta il code_verifier in chiaro e il backend verifica che l'hash combaci.
-- Nullable a livello di schema per non rompere i deploy rolling (codici effimeri, TTL ~120s);
-- la presenza e' comunque obbligatoria a livello applicativo.
ALTER TABLE auth_codes
    ADD COLUMN code_challenge VARCHAR(43) NULL AFTER user_id;
