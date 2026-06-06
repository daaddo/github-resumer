package it.davidecascella.githubresumer.service;

import it.davidecascella.githubresumer.domain.RefreshToken;
import it.davidecascella.githubresumer.repository.RefreshTokenRepository;
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
 * Gestione dei refresh token: emissione, rotazione con reuse detection e revoca.
 * Il token in chiaro è restituito una sola volta; nel DB resta solo l'hash SHA-256.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${app.refresh-token.ttl-days:60}") long ttlDays) {
        this.repository = repository;
        this.ttl = Duration.ofDays(ttlDays);
    }

    /** Esito di una rotazione: l'utente collegato e il nuovo refresh token in chiaro. */
    public record Rotated(Long userId, String refreshToken) {
    }

    /** Emette un nuovo refresh token per l'utente. */
    @Transactional
    public String issue(Long userId) {
        Instant now = Instant.now();
        repository.deleteExpired(now);
        return persistNew(userId, now);
    }

    /**
     * Ruota il refresh token: ne emette uno nuovo e revoca quello presentato.
     * Se viene ripresentato un token già ruotato, lo interpreta come furto e revoca
     * tutti i token attivi dell'utente.
     */
    @Transactional
    public Rotated rotate(String presented) {
        Instant now = Instant.now();
        RefreshToken current = repository.findByTokenHash(sha256(presented))
                .orElseThrow(() -> unauthorized("Refresh token non valido"));

        if (current.getRevokedAt() != null) {
            // Reuse detection: un token già ruotato e ripresentato = sospetto furto.
            if (current.getRotatedTo() != null) {
                repository.revokeAllForUser(current.getUserId(), now);
            }
            throw unauthorized("Refresh token revocato");
        }
        if (current.getExpiresAt().isBefore(now)) {
            throw unauthorized("Refresh token scaduto");
        }
        if (repository.markRevoked(current.getId(), now) == 0) {
            throw unauthorized("Refresh token gia usato");
        }

        String fresh = persistNew(current.getUserId(), now);
        repository.setRotatedTo(current.getId(),
                repository.findByTokenHash(sha256(fresh)).orElseThrow().getId());
        return new Rotated(current.getUserId(), fresh);
    }

    /** Revoca il refresh token presentato (logout). Idempotente. */
    @Transactional
    public void revoke(String presented) {
        repository.findByTokenHash(sha256(presented))
                .ifPresent(token -> repository.markRevoked(token.getId(), Instant.now()));
    }

    private String persistNew(Long userId, Instant now) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        repository.save(RefreshToken.builder()
                .tokenHash(sha256(token))
                .userId(userId)
                .expiresAt(now.plus(ttl))
                .createdAt(now)
                .build());
        return token;
    }

    private static ResponseStatusException unauthorized(String reason) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, reason);
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
