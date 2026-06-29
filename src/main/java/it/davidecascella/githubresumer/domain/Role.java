package it.davidecascella.githubresumer.domain;

/**
 * Ruolo applicativo dell'utente. Persistito come stringa (EnumType.STRING),
 * quindi i nomi delle costanti coincidono con i valori in colonna {@code users.role}.
 */
public enum Role {
    /** Stato iniziale alla registrazione: NESSUN accesso al riassunto IA (riceve il commit grezzo). */
    ROLE_REGISTERED,
    /** Utente abilitato: riceve il digest IA dei push. */
    ROLE_USER,
    /** Amministratore: digest IA + accesso al pannello di amministrazione. */
    ROLE_ADMIN;

    /**
     * Vero se il ruolo può ricevere il riassunto IA dei push. I soli iscritti
     * ({@link #ROLE_REGISTERED}) ne sono esclusi: a loro va il titolo/descrizione grezzi del commit.
     */
    public boolean canAccessSummaries() {
        return this != ROLE_REGISTERED;
    }
}
