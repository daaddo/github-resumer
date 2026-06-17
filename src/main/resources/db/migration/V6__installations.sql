CREATE TABLE installations (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    installation_id      BIGINT       NOT NULL,
    user_id              BIGINT       NOT NULL,
    account_login        VARCHAR(255) NULL,
    repository_selection VARCHAR(16)  NULL,
    created_at           DATETIME(6)  NULL,
    updated_at           DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_installation_id UNIQUE (installation_id),
    CONSTRAINT fk_installation_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Lega ogni tracked repo all'installazione che lo ha portato (NULL = tracciato manualmente).
-- Permette la riconciliazione scoped alla singola installazione (un utente può averne più di una).
ALTER TABLE tracked_repositories
    ADD COLUMN installation_id BIGINT NULL;

CREATE INDEX idx_tracked_installation_id ON tracked_repositories (installation_id);
