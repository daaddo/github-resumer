package it.davidecascella.githubresumer.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.util.List;

/**
 * Inizializza Firebase Admin SDK se firebase.enabled=true e il service account è disponibile.
 * Se disabilitato (o credenziali assenti) non crea il bean FirebaseMessaging: il FcmService
 * funzionerà in modalità "log only", così il backend gira anche senza Firebase configurato.
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseProperties properties) {
        if (!properties.enabled()) {
            log.warn("Firebase DISABILITATO (firebase.enabled=false): le notifiche FCM verranno solo loggate.");
            return null;
        }
        if (properties.serviceAccountPath() == null || properties.serviceAccountPath().isBlank()) {
            log.warn("firebase.enabled=true ma firebase.service-account-path non impostato: FCM in modalità log only.");
            return null;
        }
        try {
            Resource resource = new DefaultResourceLoader().getResource(properties.serviceAccountPath());
            try (InputStream in = resource.getInputStream()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(in))
                        .build();
                FirebaseApp app = FirebaseApp.getApps().isEmpty()
                        ? FirebaseApp.initializeApp(options)
                        : FirebaseApp.getInstance();
                log.info("Firebase Admin SDK inizializzato.");
                return FirebaseMessaging.getInstance(app);
            }
        } catch (Exception e) {
            log.error("Inizializzazione Firebase fallita ({}): FCM in modalità log only.", e.getMessage());
            return null;
        }
    }
}
