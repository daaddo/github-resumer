CREATE TABLE notifications (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    repo_full_name VARCHAR(255) NOT NULL,
    -- TEXT (non VARCHAR): il titolo potrebbe allungarsi in futuro. Non è indicizzato, quindi
    -- non incide su limiti di lunghezza degli indici.
    title          TEXT         NOT NULL,
    commit_id      VARCHAR(64)  NOT NULL,
    created_at     DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Unico indice: utente + repository. Serve sia lo storico per utente (prefisso user_id) sia la
-- ricerca filtrata per repository. NON indicizziamo l'intera riga: l'identità a 4 campi
-- (utente, repo, titolo, commit) che evita i duplicati è verificata a livello applicativo
-- (NotificationService.record), non imposta da un indice univoco.
CREATE INDEX idx_notifications_user_repo
    ON notifications (user_id, repo_full_name);
