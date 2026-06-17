package it.davidecascella.githubresumer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Repository che un utente ha scelto di tracciare.
 * Il webhook recapita notifiche SOLO per i repo presenti in questa tabella (la "Regola del Buttafuori").
 */
@Entity
@Table(
        name = "tracked_repositories",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_repo", columnNames = {"user_id", "repo_full_name"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackedRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Es. "torvalds/linux". Coincide col campo repository.full_name del payload del webhook. */
    @Column(name = "repo_full_name", nullable = false)
    private String repoFullName;

    /** Installazione che ha portato questo repo (NULL = tracciato manualmente dall'utente). */
    @Column(name = "installation_id")
    private Long installationId;
}
