package it.davidecascella.githubresumer.web;

import it.davidecascella.githubresumer.service.GitHubOAuthService;
import it.davidecascella.githubresumer.service.InstallationService;
import it.davidecascella.githubresumer.service.SessionTokens;
import it.davidecascella.githubresumer.web.dto.ExchangeRequest;
import it.davidecascella.githubresumer.web.dto.RefreshRequest;
import it.davidecascella.githubresumer.web.dto.SessionResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private final InstallationService installationService;

    public AuthController(GitHubOAuthService oAuthService, InstallationService installationService) {
        this.oAuthService = oAuthService;
        this.installationService = installationService;
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
        log.info("Avvio login OAuth: code_challenge_method={}, code_challenge={}", codeChallengeMethod, codeChallenge);

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
     * di login, e in OGNI caso restituisce una pagina HTML che rimbalza all'app (con ?code=... o
     * ?error=...): così l'app non resta mai bloccata nel browser. La pagina riapre l'app tramite
     * custom scheme (githubresumer://auth/callback), che dalla Custom Tab viene consegnato all'app
     * senza il blocco anti-loop dell'intent://. Il cookie di state viene sempre ripulito.
     */
    @GetMapping("/github/callback")
    public ResponseEntity<String> callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            @CookieValue(value = STATE_COOKIE, required = false) String expectedState,
            @CookieValue(value = CHALLENGE_COOKIE, required = false) String codeChallenge) {
        log.info("Callback OAuth ricevuta: code presente={}, error={}, challenge presente={}",
                code != null && !code.isBlank(), error, codeChallenge != null && !codeChallenge.isBlank());

        String body;
        if (!stateMatches(expectedState, state)) {
            log.warn("Callback OAuth con state non valido");
            body = oAuthService.buildAppErrorPage("invalid_state");
        } else if (codeChallenge == null || codeChallenge.isBlank()) {
            log.warn("Callback OAuth senza code challenge PKCE");
            body = oAuthService.buildAppErrorPage("missing_challenge");
        } else if (error != null && !error.isBlank()) {
            log.warn("Callback OAuth con errore da GitHub: {}", error);
            body = oAuthService.buildAppErrorPage(error);
        } else if (code == null || code.isBlank()) {
            log.warn("Callback OAuth senza authorization code");
            body = oAuthService.buildAppErrorPage("missing_code");
        } else {
            try {
                body = oAuthService.buildAppRedirectPage(oAuthService.completeLogin(code, codeChallenge));
                log.info("Login OAuth completato: codice usa-e-getta emesso, rimbalzo all'app");
            } catch (Exception e) {
                log.warn("Login OAuth fallito", e);
                body = oAuthService.buildAppErrorPage("login_failed");
            }
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(STATE_COOKIE, "", Duration.ZERO).toString())
                .header(HttpHeaders.SET_COOKIE, cookie(CHALLENGE_COOKIE, "", Duration.ZERO).toString())
                .contentType(MediaType.TEXT_HTML)
                .body(body);
    }

    /**
     * "Setup URL" della GitHub App: GitHub reindirizza qui dopo "Install & Authorize".
     * Riceve installation_id + setup_action e, grazie all'autorizzazione utente durante l'installazione,
     * anche un code OAuth. Identifica l'utente dal code, traccia i repo dell'installazione e rimbalza
     * all'app via custom scheme (githubresumer://...?installed=true&count=N), così non resta nel browser.
     */
    @GetMapping("/github/setup")
    public ResponseEntity<String> setup(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "installation_id", required = false) Long installationId,
            @RequestParam(value = "setup_action", required = false) String setupAction) {
        log.info("Setup installazione: installation_id={}, setup_action={}, code presente={}",
                installationId, setupAction, code != null && !code.isBlank());

        String body;
        if (installationId == null) {
            log.warn("Setup senza installation_id");
            body = oAuthService.buildAppErrorPage("missing_installation_id");
        } else if (code == null || code.isBlank()) {
            log.warn("Setup senza code: verificare che 'Request user authorization (OAuth) during installation' sia attivo");
            body = oAuthService.buildAppErrorPage("missing_code");
        } else {
            try {
                int count = installationService.linkInstallation(code, installationId);
                log.info("Installazione completata: {} repo tracciati, rimbalzo all'app", count);
                body = oAuthService.buildAppInstalledPage(count);
            } catch (Exception e) {
                log.warn("Setup installazione fallito", e);
                body = oAuthService.buildAppErrorPage("install_failed");
            }
        }

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(body);
    }

    /** L'app scambia il codice usa-e-getta con access token + refresh token (back-channel HTTPS). */
    @PostMapping("/exchange")
    public SessionResponse exchange(@Valid @RequestBody ExchangeRequest request) {
        log.info("Richiesta scambio codice usa-e-getta -> sessione");
        SessionTokens tokens = oAuthService.exchangeCodeForSession(request.code(), request.codeVerifier());
        log.info("Scambio riuscito: access token + refresh token emessi");
        return new SessionResponse(tokens.accessToken(), tokens.refreshToken());
    }

    /** Rinnova la sessione: ruota il refresh token e restituisce un nuovo access token. */
    @PostMapping("/refresh")
    public SessionResponse refresh(@Valid @RequestBody RefreshRequest request) {
        log.info("Richiesta refresh sessione");
        SessionTokens tokens = oAuthService.refreshSession(request.refreshToken());
        log.info("Sessione rinnovata: refresh token ruotato, nuovo access token emesso");
        return new SessionResponse(tokens.accessToken(), tokens.refreshToken());
    }

    /** Logout: revoca il refresh token presentato. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        log.info("Richiesta logout: revoca refresh token");
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
