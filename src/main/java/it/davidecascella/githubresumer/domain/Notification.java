package it.davidecascella.githubresumer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Notifica recapitata a un utente per l'attività di un repository tracciato.
 * È la copia persistente di ciò che il webhook invia via FCM, così l'app può rileggere
 * lo storico in modo paginato e cronologico anche dopo che la push notification è sparita.
 *
 * <p>Identità di business (la "stessa" notifica): utente destinatario + repository + titolo +
 * id del commit. La deduplica su questi quattro campi è verificata a livello applicativo
 * (NotificationService.record), così un re-delivery del webhook non duplica lo storico,
 * senza dover indicizzare l'intera riga.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Utente destinatario della notifica. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Es. "torvalds/linux". */
    @Column(name = "repo_full_name", nullable = false)
    private String repoFullName;

    /**
     * Testo mostrato in notifica (il riassunto del push). Mappato su una colonna TEXT
     * (LONGVARCHAR) perché in futuro potrebbe allungarsi oltre i limiti di un VARCHAR.
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "title", nullable = false)
    private String title;

    /**
     * Descrizione più estesa delle modifiche (max ~450 caratteri, prodotta dall'LLM dal diff).
     * Colonna TEXT e nullable: le notifiche storiche precedenti a questa feature non l'hanno.
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "description")
    private String description;

    /** SHA del commit di riferimento (head del push). */
    @Column(name = "commit_id", nullable = false, length = 64)
    private String commitId;

    /** Istante di creazione: usato per l'ordinamento cronologico dello storico. */
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
