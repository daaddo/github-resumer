package it.davidecascella.githubresumer.service;

import io.jsonwebtoken.Jwts;
import it.davidecascella.githubresumer.client.GitHubApiClient;
import it.davidecascella.githubresumer.client.dto.InstallationTokenResponse;
import it.davidecascella.githubresumer.config.GitHubProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Autenticazione "come GitHub App":
 * - genera il JWT firmato RS256 con la private key della App (valido max 10 min);
 * - lo scambia con un installation access token per leggere i repo dell'installazione.
 *
 * NB: la private key in github.private-key deve essere in formato PKCS#8.
 * GitHub fornisce un .pem PKCS#1: convertilo una volta con
 *   openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in app.private-key.pem -out app.pkcs8.pem
 */
@Slf4j
@Service
public class GitHubAppService {

    private final GitHubProperties properties;
    private final GitHubApiClient gitHubApiClient;
    private final PrivateKey privateKey;

    public GitHubAppService(GitHubProperties properties, GitHubApiClient gitHubApiClient) {
        this.properties = properties;
        this.gitHubApiClient = gitHubApiClient;
        this.privateKey = parsePrivateKeyOrNull(properties.privateKey());
    }

    /** JWT della App firmato RS256 (iss = appId). */
    public String generateAppJwt() {
        if (privateKey == null) {
            throw new IllegalStateException("github.private-key non configurata o non valida (serve PKCS#8).");
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.appId())
                // 60s di backdating per tollerare lo skew dell'orologio, come consiglia GitHub.
                .issuedAt(Date.from(now.minusSeconds(60)))
                .expiration(Date.from(now.plusSeconds(540)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /** Installation access token (Bearer) per chiamare le API sui repo dell'installazione. */
    public String getInstallationToken(long installationId) {
        InstallationTokenResponse response =
                gitHubApiClient.createInstallationToken(installationId, "Bearer " + generateAppJwt());
        return response.token();
    }

    /** Disinstalla la App da questa installazione (rifiuto di un'installazione non ammessa). */
    public void deleteInstallation(long installationId) {
        gitHubApiClient.deleteInstallation(installationId, "Bearer " + generateAppJwt());
    }

    private PrivateKey parsePrivateKeyOrNull(String pem) {
        if (pem == null || pem.isBlank()) {
            return null;
        }
        try {
            String normalized = pem
                    .replace("\\n", "\n")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(normalized);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            log.error("Impossibile leggere github.private-key (formato atteso: PKCS#8 PEM): {}", e.getMessage());
            return null;
        }
    }
}
