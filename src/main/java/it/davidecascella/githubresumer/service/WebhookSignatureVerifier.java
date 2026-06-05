package it.davidecascella.githubresumer.service;

import it.davidecascella.githubresumer.config.GitHubProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Verifica la firma HMAC-SHA256 dei webhook (header X-Hub-Signature-256). */
@Component
public class WebhookSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";
    private final String secret;

    public WebhookSignatureVerifier(GitHubProperties properties) {
        this.secret = properties.webhookSecret();
    }

    /**
     * @param payload         corpo grezzo della richiesta (byte esatti ricevuti)
     * @param signatureHeader valore di X-Hub-Signature-256, formato "sha256=...."
     * @return true se la firma è valida
     */
    public boolean isValid(byte[] payload, String signatureHeader) {
        if (secret == null || secret.isBlank() || signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] computed = mac.doFinal(payload);
            String expected = "sha256=" + HexFormat.of().formatHex(computed);
            // Confronto a tempo costante per evitare timing attack.
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
