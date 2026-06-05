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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

import java.time.Instant;

/**
 * Utente identificato tramite il login OAuth di GitHub.
 * Conserva il token FCM usato per recapitare le notifiche push al telefono Android.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "github_username", nullable = false, unique = true)
    private String githubUsername;

    /** ID numerico univoco dell'account su GitHub (stabile anche se l'utente rinomina lo username). */
    @Column(name = "github_account_id", nullable = false, unique = true)
    private Long githubAccountId;

    /** Token FCM del dispositivo; può essere null finché il client non lo registra. */
    @Column(name = "fcm_token", length = 512)
    private String fcmToken;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
