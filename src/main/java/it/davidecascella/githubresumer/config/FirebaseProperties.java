package it.davidecascella.githubresumer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurazione di Firebase Admin SDK (FCM).
 *
 * @param enabled               Se false, il FcmService logga soltanto senza inviare (utile in locale senza credenziali).
 * @param serviceAccountPath    Path al file JSON del service account (es. file:./firebase-service-account.json o classpath:...).
 */
@ConfigurationProperties(prefix = "firebase")
public record FirebaseProperties(
        boolean enabled,
        String serviceAccountPath
) {
}
