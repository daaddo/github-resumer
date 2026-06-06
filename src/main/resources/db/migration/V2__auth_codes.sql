-- Codici OAuth usa-e-getta: legano un codice effimero (di cui salviamo solo l'hash)
-- all'utente, per essere scambiati con un JWT di sessione via POST /auth/exchange.
CREATE TABLE auth_codes (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code_hash   VARCHAR(64)  NOT NULL,
    user_id     BIGINT       NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    consumed_at DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_codes_hash UNIQUE (code_hash),
    CONSTRAINT fk_auth_codes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
