-- Refresh token: parte STATEFUL della sessione. L'access token (JWT) resta breve e stateless,
-- il refresh token vive a lungo, è salvato solo come hash ed è revocabile (logout / furto).
-- rotated_to collega un token al suo successore: se un token già ruotato viene ripresentato,
-- è il segnale di un furto (reuse detection) e si revoca l'intera famiglia dell'utente.
CREATE TABLE refresh_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    token_hash VARCHAR(64)  NOT NULL,
    user_id    BIGINT       NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    revoked_at DATETIME(6)  NULL,
    rotated_to BIGINT       NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
