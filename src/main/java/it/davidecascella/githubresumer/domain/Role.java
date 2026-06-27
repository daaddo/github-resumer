package it.davidecascella.githubresumer.domain;

/**
 * Ruolo applicativo dell'utente. Persistito come stringa (EnumType.STRING),
 * quindi i nomi delle costanti coincidono con i valori in colonna {@code users.role}.
 */
public enum Role {
    ROLE_USER,
    ROLE_ADMIN
}
