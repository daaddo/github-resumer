-- Descrizione estesa delle modifiche (prodotta dall'LLM dal diff), accanto al titolo.
-- Nullable: le notifiche storiche, create prima di questa feature, non l'hanno.
ALTER TABLE notifications
    ADD COLUMN description TEXT NULL AFTER title;
