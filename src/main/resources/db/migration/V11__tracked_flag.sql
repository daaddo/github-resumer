-- Distingue "followed" (riga presente: il repo è nella lista dell'utente, importato
-- dall'installazione o aggiunto a mano) da "tracked" (l'utente vuole davvero il digest).
-- DEFAULT TRUE: opt-out, così i repo già importati continuano a notificare come prima.
ALTER TABLE tracked_repositories
    ADD COLUMN tracked BOOLEAN NOT NULL DEFAULT TRUE;

-- Il webhook filtra per (repo_full_name, tracked): indice di copertura per non scansionare.
CREATE INDEX idx_tracked_repo_tracked ON tracked_repositories (repo_full_name, tracked);
