package it.davidecascella.githubresumer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurazione del JWT di sessione emesso al client Android dopo il login OAuth.
 *
 * @param secret              Segreto HMAC (>= 32 byte) per firmare gli access token.
 * @param accessTokenMinutes  Validità dell'access token in minuti (breve: la revoca passa dal refresh token).
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        Long accessTokenMinutes
) {
    public JwtProperties {
        if (accessTokenMinutes == null || accessTokenMinutes <= 0) {
            accessTokenMinutes = 15L;
        }
    }
}
