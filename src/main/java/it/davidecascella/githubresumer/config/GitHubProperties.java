package it.davidecascella.githubresumer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurazione della GitHub App e dell'OAuth.
 * Valori reali in application-local.properties (git-ignored).
 *
 * @param appId            ID numerico della GitHub App.
 * @param privateKey       Chiave privata PEM della App (per firmare il JWT della App, RS256).
 * @param webhookSecret    Segreto condiviso per verificare la firma HMAC dei webhook.
 * @param clientId         OAuth client id.
 * @param clientSecret     OAuth client secret.
 * @param callbackBaseUrl  URL pubblico del backend usato come base per il redirect OAuth.
 * @param appRedirectUri   Deep link dell'app Android a cui rimbalzare il JWT di sessione dopo il login.
 */
@ConfigurationProperties(prefix = "github")
public record GitHubProperties(
        String appId,
        String privateKey,
        String webhookSecret,
        String clientId,
        String clientSecret,
        String callbackBaseUrl,
        String appRedirectUri
) {
}
