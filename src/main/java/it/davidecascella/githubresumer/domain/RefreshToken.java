package it.davidecascella.githubresumer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Refresh token di sessione (parte stateful). Del token in chiaro conserviamo solo l'hash SHA-256.
 * Revocabile (logout) e soggetto a rotazione: ogni uso ne emette uno nuovo e revoca il vecchio.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Valorizzato al logout o alla rotazione: un token revocato non è più scambiabile. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Id del token che ha sostituito questo nella rotazione (per la reuse detection). */
    @Column(name = "rotated_to")
    private Long rotatedTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
