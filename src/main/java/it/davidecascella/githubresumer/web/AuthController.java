package it.davidecascella.githubresumer.web;

import it.davidecascella.githubresumer.service.GitHubOAuthService;
import it.davidecascella.githubresumer.service.SessionTokens;
import it.davidecascella.githubresumer.web.dto.ExchangeRequest;
import it.davidecascella.githubresumer.web.dto.RefreshRequest;
import it.davidecascella.githubresumer.web.dto.SessionResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

/** Flusso di login OAuth con GitHub. */
@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    /** Cookie che custodisce lo state OAuth tra /login e /callback (anti-CSRF). */
    private static final String STATE_COOKIE = "gh_oauth_state";
    /** Cookie che porta la code challenge PKCE da /login fino a /callback, dove viene legata al codice. */
    private static final String CHALLENGE_COOKIE = "gh_oauth_challenge";
    private static final String COOKIE_PATH = "/auth/github";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    /** PKCE: l'unico metodo accettato e' S256 (RFC 7636); 'plain' non e' ammesso in produzione. */
    private static final String PKCE_METHOD_S256 = "S256";
    /** Code challenge S256 = BASE64URL(SHA-256) senza padding: esattamente 43 caratteri url-safe. */
    private static final Pattern CHALLENGE_PATTERN = Pattern.compile("[A-Za-z0-9\\-_]{43}");

    private final GitHubOAuthService oAuthService;

    public AuthController(GitHubOAuthService oAuthService) {
        this.oAuthService = oAuthService;
    }

    /**
     * L'app apre questo URL nel browser per avviare il login. Oltre allo state anti-CSRF, l'app
     * presenta la code challenge PKCE (metodo S256): state e challenge vengono depositati in due
     * cookie HttpOnly e recuperati al callback. La challenge sara' poi legata al codice usa-e-getta.
     */
    @GetMapping("/github/login")
    public ResponseEntity<Void> login(
            @RequestParam("code_challenge") String codeChallenge,
            @RequestParam("code_challenge_method") String codeChallengeMethod) {

        if (!PKCE_METHOD_S256.equals(codeChallengeMethod)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code_challenge_method deve essere S256");
        }
        if (codeChallenge == null || !CHALLENGE_PATTERN.matcher(codeChallenge).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code_challenge non valida");
        }

        String state = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, cookie(STATE_COOKIE, state, STATE_TTL).toString())
                .header(HttpHeaders.SET_COOKIE, cookie(CHALLENGE_COOKIE, codeChallenge, STATE_TTL).toString())
                .location(URI.create(oAuthService.buildAuthorizeUrl(state)))
                .build();
    }

    /**
     * Callback chiamata da GitHub. Verifica lo state, gestisce il rifiuto/gli errori e i fallimenti
     * di login, e in OGNI caso rimbalza all'App Link (con ?code=... o ?error=...): così l'app non
     * resta mai bloccata nel browser. Il cookie di state viene sempre ripulito.
     */
    @GetMapping("/github/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            @CookieValue(value = STATE_COOKIE, required = false) String expectedState,
            @CookieValue(value = CHALLENGE_COOKIE, required = false) String codeChallenge) {

        String target;
        if (!stateMatches(expectedState, state)) {
            log.warn("Callback OAuth con state non valido");
            target = oAuthService.buildAppErrorRedirect("invalid_state");
        } else if (codeChallenge == null || codeChallenge.isBlank()) {
            log.warn("Callback OAuth senza code challenge PKCE");
            target = oAuthService.buildAppErrorRedirect("missing_challenge");
        } else if (error != null && !error.isBlank()) {
            target = oAuthService.buildAppErrorRedirect(error);
        } else if (code == null || code.isBlank()) {
            target = oAuthService.buildAppErrorRedirect("missing_code");
        } else {
            try {
                target = oAuthService.buildAppRedirect(oAuthService.completeLogin(code, codeChallenge));
            } catch (Exception e) {
                log.warn("Login OAuth fallito", e);
                target = oAuthService.buildAppErrorRedirect("login_failed");
            }
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, cookie(STATE_COOKIE, "", Duration.ZERO).toString())
                .header(HttpHeaders.SET_COOKIE, cookie(CHALLENGE_COOKIE, "", Duration.ZERO).toString())
                .location(URI.create(target))
                .build();
    }

    /** L'app scambia il codice usa-e-getta con access token + refresh token (back-channel HTTPS). */
    @PostMapping("/exchange")
    public SessionResponse exchange(@Valid @RequestBody ExchangeRequest request) {
        SessionTokens tokens = oAuthService.exchangeCodeForSession(request.code(), request.codeVerifier());
        return new SessionResponse(tokens.accessToken(), tokens.refreshToken());
    }

    /** Rinnova la sessione: ruota il refresh token e restituisce un nuovo access token. */
    @PostMapping("/refresh")
    public SessionResponse refresh(@Valid @RequestBody RefreshRequest request) {
        SessionTokens tokens = oAuthService.refreshSession(request.refreshToken());
        return new SessionResponse(tokens.accessToken(), tokens.refreshToken());
    }

    /** Logout: revoca il refresh token presentato. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        oAuthService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private static ResponseCookie cookie(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    /** Confronto a tempo costante: lo state deve essere presente in entrambi e identico. */
    private static boolean stateMatches(String expected, String received) {
        if (expected == null || received == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8));
    }
}
