package it.davidecascella.githubresumer.service;

import it.davidecascella.githubresumer.domain.AuthCode;
import it.davidecascella.githubresumer.repository.AuthCodeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Emette e scambia i codici OAuth usa-e-getta. Il codice in chiaro viaggia una sola volta
 * nel redirect verso l'app; nel DB resta solo il suo hash SHA-256.
 */
@Service
public class AuthCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthCodeRepository repository;
    private final Duration ttl;

    public AuthCodeService(AuthCodeRepository repository,
                           @Value("${app.auth-code.ttl-seconds:120}") long ttlSeconds) {
        this.repository = repository;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /**
     * Genera un codice opaco legato all'utente, ne salva solo l'hash insieme alla code challenge PKCE
     * e restituisce il codice in chiaro. La challenge sara' verificata contro il verifier all'exchange.
     */
    @Transactional
    public String issue(Long userId, String codeChallenge) {
        Instant now = Instant.now();
        repository.deleteExpired(now);

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        repository.save(AuthCode.builder()
                .codeHash(sha256Hex(code))
                .userId(userId)
                .codeChallenge(codeChallenge)
                .expiresAt(now.plus(ttl))
                .createdAt(now)
                .build());
        return code;
    }

    /**
     * Consuma il codice (una sola volta, se non scaduto) verificando la prova PKCE: l'hash
     * BASE64URL(SHA-256(codeVerifier)) deve combaciare con la challenge salvata all'emissione.
     * Solo allora il codice viene marcato come usato e si restituisce l'id utente.
     */
    @Transactional
    public Long consume(String code, String codeVerifier) {
        AuthCode entity = repository.findByCodeHash(sha256Hex(code))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codice non valido"));
        if (entity.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codice scaduto");
        }
        if (!pkceMatches(entity.getCodeChallenge(), codeVerifier)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verifica PKCE fallita");
        }
        if (repository.markConsumed(entity.getId(), Instant.now()) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codice gia utilizzato");
        }
        return entity.getUserId();
    }

    /** Confronto a tempo costante tra la challenge salvata e BASE64URL(SHA-256(verifier)). */
    private static boolean pkceMatches(String storedChallenge, String codeVerifier) {
        if (storedChallenge == null || codeVerifier == null) {
            return false;
        }
        String computed = base64UrlSha256(codeVerifier);
        return MessageDigest.isEqual(
                storedChallenge.getBytes(StandardCharsets.UTF_8),
                computed.getBytes(StandardCharsets.UTF_8));
    }

    /** SHA-256 in hex: usato per non memorizzare mai il codice in chiaro. */
    private static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value));
    }

    /** SHA-256 codificato in BASE64URL senza padding: e' il formato della code challenge PKCE (S256). */
    private static String base64UrlSha256(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(value));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponibile", e);
        }
    }
}
