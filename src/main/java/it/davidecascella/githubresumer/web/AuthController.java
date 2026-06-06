package it.davidecascella.githubresumer.web;

import it.davidecascella.githubresumer.service.GitHubOAuthService;
import it.davidecascella.githubresumer.service.SessionTokens;
import it.davidecascella.githubresumer.web.dto.ExchangeRequest;
import it.davidecascella.githubresumer.web.dto.RefreshRequest;
import it.davidecascella.githubresumer.web.dto.SessionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URI;
import java.util.UUID;

/** Flusso di login OAuth con GitHub. */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final GitHubOAuthService oAuthService;

    public AuthController(GitHubOAuthService oAuthService) {
        this.oAuthService = oAuthService;
    }

    /** L'app apre questo URL nel browser per avviare il login. */
    @GetMapping("/github/login")
    public RedirectView login() {
        String state = UUID.randomUUID().toString();
        return new RedirectView(oAuthService.buildAuthorizeUrl(state));
    }

    /**
     * Callback chiamata da GitHub: completa il login e rimbalza all'App Link un codice
     * usa-e-getta (non il JWT). L'app intercetta il redirect e chiama {@link #exchange}.
     */
    @GetMapping("/github/callback")
    public ResponseEntity<Void> callback(@RequestParam("code") String code) {
        String oneTimeCode = oAuthService.completeLogin(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(oAuthService.buildAppRedirect(oneTimeCode)))
                .build();
    }

    /** L'app scambia il codice usa-e-getta con access token + refresh token (back-channel HTTPS). */
    @PostMapping("/exchange")
    public SessionResponse exchange(@Valid @RequestBody ExchangeRequest request) {
        SessionTokens tokens = oAuthService.exchangeCodeForSession(request.code());
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
}
