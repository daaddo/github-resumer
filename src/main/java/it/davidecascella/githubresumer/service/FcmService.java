package it.davidecascella.githubresumer.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Invio di notifiche push silenziose/compatte via FCM.
 * Usa messaggi "data-only" (niente blocco notification): è il client Android a decidere
 * come mostrarli sullo smartwatch. Se Firebase non è configurato, logga soltanto.
 */
@Slf4j
@Service
public class FcmService {

    @Nullable
    private final FirebaseMessaging firebaseMessaging;

    public FcmService(@Nullable FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    public void sendCommitSummary(String fcmToken, String repoFullName, String summary) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.debug("Nessun token FCM per il repo {}: notifica saltata.", repoFullName);
            return;
        }
        Map<String, String> data = Map.of(
                "type", "commit_summary",
                "repo", repoFullName,
                "summary", summary
        );

        if (firebaseMessaging == null) {
            log.info("[FCM log-only] token={} repo={} summary={}", mask(fcmToken), repoFullName, summary);
            return;
        }
        try {
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .putAllData(data)
                    .build();
            String id = firebaseMessaging.send(message);
            log.info("Notifica FCM inviata (id={}) per repo {}.", id, repoFullName);
        } catch (Exception e) {
            log.error("Invio FCM fallito per repo {}: {}", repoFullName, e.getMessage());
        }
    }

    private String mask(String token) {
        return token.length() <= 8 ? "****" : token.substring(0, 6) + "…";
    }
}
