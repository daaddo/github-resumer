package it.davidecascella.githubresumer.web;

import it.davidecascella.githubresumer.domain.Role;
import it.davidecascella.githubresumer.domain.User;
import it.davidecascella.githubresumer.repository.UserRepository;
import it.davidecascella.githubresumer.service.GitHubOAuthService;
import it.davidecascella.githubresumer.service.JwtService;
import it.davidecascella.githubresumer.web.security.AdminCookieAuthenticationFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Pannello di amministrazione server-side (Thymeleaf). Login via OAuth GitHub con cookie di sessione
 * HttpOnly: distinto dal login dell'app (bearer JWT) e con redirect_uri dedicata (/admin/oauth/callback).
 */
@Slf4j
@Controller
@RequestMapping("/admin")
public class AdminWebController {

    /** Cookie anti-CSRF che custodisce lo state OAuth tra start e callback. */
    private static final String STATE_COOKIE = "gh_admin_state";
    private static final String COOKIE_PATH = "/admin";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final GitHubOAuthService oAuthService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AdminWebController(GitHubOAuthService oAuthService,
                             JwtService jwtService,
                             UserRepository userRepository) {
        this.oAuthService = oAuthService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    /** Pagina di login. {@code error} opzionale per mostrare messaggi (auth fallita / non admin). */
    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error, Model model) {
        model.addAttribute("error", error);
        return "admin/login";
    }

    /** Avvia il login: genera lo state, lo deposita in cookie e reindirizza a GitHub. */
    @GetMapping("/oauth/start")
    public ResponseEntity<Void> start() {
        String state = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, stateCookie(state, STATE_TTL).toString())
                .location(URI.create(oAuthService.buildWebAuthorizeUrl(state)))
                .build();
    }

    /** Callback OAuth: verifica state, identifica l'utente, controlla il ruolo e setta il cookie di sessione. */
    @GetMapping("/oauth/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @CookieValue(value = STATE_COOKIE, required = false) String expectedState) {

        if (!stateMatches(expectedState, state) || code == null || code.isBlank()) {
            log.warn("Callback admin web non valida (state mismatch o code assente)");
            return redirect("/admin/login?error=auth", clearState());
        }

        Optional<User> user;
        try {
            user = oAuthService.completeWebLogin(code);
        } catch (Exception e) {
            log.warn("Login admin web fallito", e);
            return redirect("/admin/login?error=auth", clearState());
        }

        if (user.isEmpty() || user.get().getRole() != Role.ROLE_ADMIN) {
            log.info("Accesso pannello negato: account non admin o sconosciuto");
            return redirect("/admin/login?error=forbidden", clearState());
        }

        User admin = user.get();
        String jwt = jwtService.issueAccessToken(admin.getId(), admin.getGithubUsername());
        log.info("Login pannello admin riuscito per {}", admin.getGithubUsername());
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, clearState().toString())
                .header(HttpHeaders.SET_COOKIE, sessionCookie(jwt).toString())
                .location(URI.create("/admin"))
                .build();
    }

    /** Dashboard: elenco utenti. L'accesso è già garantito admin dalla SecurityConfig. */
    @GetMapping({"", "/"})
    public String dashboard(@AuthenticationPrincipal Long adminId, Model model) {
        if (adminId != null) {
            userRepository.findById(adminId).ifPresent(u -> model.addAttribute("admin", u));
        }
        model.addAttribute("users", userRepository.findAll());
        return "admin/dashboard";
    }

    /** Logout: cancella il cookie di sessione e torna al login. */
    @GetMapping("/logout")
    public ResponseEntity<Void> logout() {
        return redirect("/admin/login", expiredSession());
    }

    // --- helper cookie / redirect ---

    private static ResponseEntity<Void> redirect(String location, ResponseCookie cookie) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .location(URI.create(location))
                .build();
    }

    private static ResponseCookie stateCookie(String value, Duration maxAge) {
        return baseCookie(STATE_COOKIE, value).maxAge(maxAge).build();
    }

    private static ResponseCookie clearState() {
        return baseCookie(STATE_COOKIE, "").maxAge(Duration.ZERO).build();
    }

    private static ResponseCookie sessionCookie(String jwt) {
        // Cookie di sessione (niente maxAge → si chiude col browser). La validità effettiva
        // è governata dalla scadenza del JWT: se scade, la SecurityConfig rimanda al login.
        return baseCookie(AdminCookieAuthenticationFilter.SESSION_COOKIE, jwt).build();
    }

    private static ResponseCookie expiredSession() {
        return baseCookie(AdminCookieAuthenticationFilter.SESSION_COOKIE, "").maxAge(Duration.ZERO).build();
    }

    private static ResponseCookie.ResponseCookieBuilder baseCookie(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(COOKIE_PATH);
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
