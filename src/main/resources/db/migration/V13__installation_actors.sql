-- Tracciamo gli attori GitHub dell'evento "installation/created": requester (chi ha richiesto,
-- valorizzato solo nel flusso richiesta->approvazione su org) e sender (chi ha scatenato l'evento).
-- Servono al webhook per legare l'installazione all'utente giusto; restano anche per audit.
ALTER TABLE installations
    ADD COLUMN requester_github_id BIGINT NULL,
    ADD COLUMN sender_github_id    BIGINT NULL;
