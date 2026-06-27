package it.davidecascella.githubresumer.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
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

    /**
     * Esito dell'invio a un singolo token. {@link #INVALID_TOKEN} segnala al chiamante che
     * il token va rimosso dal registro dei dispositivi (device disinstallato o token scaduto).
     */
    public enum SendOutcome {
        SENT, SKIPPED, INVALID_TOKEN, ERROR
    }

    @Nullable
    private final FirebaseMessaging firebaseMessaging;

    public FcmService(@Nullable FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    public SendOutcome sendCommitSummary(String fcmToken, String repoFullName, String title, String description) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.debug("Nessun token FCM per il repo {}: notifica saltata.", repoFullName);
            return SendOutcome.SKIPPED;
        }
        // FCM rifiuta valori null nei data: normalizziamo a stringa vuota.
        String safeTitle = title == null ? "" : title;
        String safeDescription = description == null ? "" : description;
        Map<String, String> data = Map.of(
                "type", "commit_summary",
                "repo", repoFullName,
                "title", safeTitle,
                "description", safeDescription,
                // Ponte di compatibilità: i client che leggono ancora "summary" continuano a funzionare.
                "summary", safeTitle
        );

        if (firebaseMessaging == null) {
            log.info("[FCM log-only] token={} repo={} title={} descLen={}",
                    mask(fcmToken), repoFullName, safeTitle, safeDescription.length());
            return SendOutcome.SKIPPED;
        }
        try {
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .putAllData(data)
                    // Priorità alta: indispensabile per i messaggi data-only, altrimenti in Doze/background
                    // FCM può ritardarli o non svegliare il dispositivo.
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .build();
            String id = firebaseMessaging.send(message);
            log.info("Notifica FCM inviata (id={}) per repo {}.", id, repoFullName);
            return SendOutcome.SENT;
        } catch (FirebaseMessagingException e) {
            if (isTokenInvalid(e)) {
                log.info("Token FCM non più valido ({}) per repo {}: da rimuovere.",
                        e.getMessagingErrorCode(), repoFullName);
                return SendOutcome.INVALID_TOKEN;
            }
            log.error("Invio FCM fallito per repo {}: {}", repoFullName, e.getMessage());
            return SendOutcome.ERROR;
        } catch (Exception e) {
            log.error("Invio FCM fallito per repo {}: {}", repoFullName, e.getMessage());
            return SendOutcome.ERROR;
        }
    }

    /** UNREGISTERED = token revocato dal device; INVALID_ARGUMENT = token malformato: in entrambi i casi va rimosso. */
    private static boolean isTokenInvalid(FirebaseMessagingException e) {
        MessagingErrorCode code = e.getMessagingErrorCode();
        return code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT;
    }

    private String mask(String token) {
        return token.length() <= 8 ? "****" : token.substring(0, 6) + "…";
    }
}
