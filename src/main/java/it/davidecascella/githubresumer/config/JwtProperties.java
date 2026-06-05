package it.davidecascella.githubresumer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurazione del JWT di sessione emesso al client Android dopo il login OAuth.
 *
 * @param secret           Segreto HMAC (>= 32 byte) per firmare i JWT di sessione.
 * @param expirationDays   Validità del token di sessione in giorni.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        Long expirationDays
) {
    public JwtProperties {
        if (expirationDays == null || expirationDays <= 0) {
            expirationDays = 30L;
        }
    }
}
