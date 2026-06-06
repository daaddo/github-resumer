package it.davidecascella.githubresumer.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import it.davidecascella.githubresumer.config.JwtProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Emette e valida l'access token (JWT breve) usato come Bearer su /api/**.
 * Il subject del token è l'id interno dell'utente.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(JwtProperties properties) {
        if (properties.secret() == null || properties.secret().length() < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret mancante o troppo corto: servono almeno 32 caratteri.");
        }
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofMinutes(properties.accessTokenMinutes());
    }

    public String issueAccessToken(Long userId, String githubUsername) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", githubUsername)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /** Estrae l'id utente dal token; lancia un'eccezione se il token è invalido o scaduto. */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.valueOf(claims.getSubject());
    }
}
