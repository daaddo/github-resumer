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

    /** Genera un codice opaco legato all'utente, ne salva solo l'hash e restituisce il codice in chiaro. */
    @Transactional
    public String issue(Long userId) {
        Instant now = Instant.now();
        repository.deleteExpired(now);

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        repository.save(AuthCode.builder()
                .codeHash(sha256(code))
                .userId(userId)
                .expiresAt(now.plus(ttl))
                .createdAt(now)
                .build());
        return code;
    }

    /** Consuma il codice (una sola volta, se non scaduto) e restituisce l'id utente associato. */
    @Transactional
    public Long consume(String code) {
        AuthCode entity = repository.findByCodeHash(sha256(code))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codice non valido"));
        if (entity.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codice scaduto");
        }
        if (repository.markConsumed(entity.getId(), Instant.now()) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codice gia utilizzato");
        }
        return entity.getUserId();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponibile", e);
        }
    }
}
