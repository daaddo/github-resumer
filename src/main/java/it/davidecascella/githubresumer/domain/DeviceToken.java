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
 * Token FCM di un singolo dispositivo. Un utente può averne più d'uno (un device per riga),
 * così le notifiche push raggiungono tutti i suoi dispositivi e non solo l'ultimo registrato.
 * Il token in sé identifica l'installazione: è UNIQUE, quindi un device appartiene a un solo utente.
 */
@Entity
@Table(name = "device_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "fcm_token", nullable = false, unique = true, length = 512)
    private String fcmToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Aggiornato a ogni ri-registrazione dello stesso token: utile per ripulire i device dormienti. */
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
}
