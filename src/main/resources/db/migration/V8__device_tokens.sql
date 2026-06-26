-- Supporto multi-dispositivo per le notifiche push: un utente può registrare N token FCM.
-- Sostituisce il vecchio campo single-device users.fcm_token (sovrascritto a ogni nuovo device).
CREATE TABLE device_tokens (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    fcm_token    VARCHAR(512) NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    last_seen_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- Il token FCM identifica univocamente l'installazione: un device appartiene a un solo utente.
    -- Se lo stesso device fa login con un altro account, la riga viene riassegnata (vedi DeviceTokenService).
    CONSTRAINT uk_device_tokens_fcm_token UNIQUE (fcm_token),
    CONSTRAINT fk_device_token_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_device_tokens_user ON device_tokens (user_id);

-- Migrazione dati: porta i token già registrati dal vecchio campo single-device.
INSERT INTO device_tokens (user_id, fcm_token, created_at, last_seen_at)
SELECT id, fcm_token, NOW(6), NOW(6)
FROM users
WHERE fcm_token IS NOT NULL AND fcm_token <> '';

ALTER TABLE users DROP COLUMN fcm_token;
