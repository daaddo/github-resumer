package it.davidecascella.githubresumer.service;

import it.davidecascella.githubresumer.domain.DeviceToken;
import it.davidecascella.githubresumer.repository.DeviceTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Registro dei token FCM per dispositivo. Sostituisce il vecchio campo single-device su User:
 * un utente può registrare più dispositivi e le notifiche vanno a tutti.
 */
@Slf4j
@Service
public class DeviceTokenService {

    private final DeviceTokenRepository repository;

    public DeviceTokenService(DeviceTokenRepository repository) {
        this.repository = repository;
    }

    /**
     * Registra (o aggiorna) il token FCM di un dispositivo per l'utente.
     * Il token è UNIQUE: se già presente lo riassegna all'utente corrente e ne aggiorna il last_seen
     * (copre il caso in cui lo stesso device faccia login con un altro account); altrimenti lo crea.
     */
    @Transactional
    public void register(Long userId, String fcmToken) {
        Instant now = Instant.now();
        DeviceToken device = repository.findByFcmToken(fcmToken)
                .orElseGet(() -> DeviceToken.builder().fcmToken(fcmToken).createdAt(now).build());
        device.setUserId(userId);
        device.setLastSeenAt(now);
        repository.save(device);
    }

    /** Tutti i token FCM registrati dall'utente (un elemento per dispositivo). */
    @Transactional(readOnly = true)
    public List<String> tokensFor(Long userId) {
        return repository.findByUserId(userId).stream()
                .map(DeviceToken::getFcmToken)
                .toList();
    }

    /** Rimuove un token che FCM ha segnalato come non più valido. Idempotente. */
    @Transactional
    public void remove(String fcmToken) {
        if (repository.deleteByFcmToken(fcmToken) > 0) {
            log.info("Token FCM invalido rimosso dal registro dei dispositivi.");
        }
    }

    /** Rimuove tutti i dispositivi dell'utente (parte del logout globale). */
    @Transactional
    public void removeAllForUser(Long userId) {
        int removed = repository.deleteByUserId(userId);
        log.info("Logout globale utente {}: {} dispositivi rimossi dal registro FCM.", userId, removed);
    }
}
