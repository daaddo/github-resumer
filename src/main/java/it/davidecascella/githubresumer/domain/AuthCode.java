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
 * Codice OAuth usa-e-getta emesso al termine del login e scambiato dall'app con un JWT di sessione
 * (POST /auth/exchange). Del codice in chiaro conserviamo solo l'hash SHA-256.
 */
@Entity
@Table(name = "auth_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Hash SHA-256 (hex) del codice in chiaro: non memorizziamo mai il codice stesso. */
    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    private String codeHash;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Valorizzato quando il codice viene scambiato: garantisce l'uso singolo. */
    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
