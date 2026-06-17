package it.davidecascella.githubresumer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Installazione della GitHub App legata a un utente. Un utente può averne più di una
 * (account personale + ogni organizzazione). Serve a mappare installation_id -> utente
 * quando arriva un webhook (server-to-server, senza user token).
 */
@Entity
@Table(
        name = "installations",
        uniqueConstraints = @UniqueConstraint(name = "uk_installation_id", columnNames = "installation_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Installation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID numerico dell'installazione su GitHub. */
    @Column(name = "installation_id", nullable = false, unique = true)
    private Long installationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Login dell'account/org su cui è installata (informativo). */
    @Column(name = "account_login")
    private String accountLogin;

    /** "all" oppure "selected": modalità di selezione repo scelta dall'utente. */
    @Column(name = "repository_selection")
    private String repositorySelection;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
