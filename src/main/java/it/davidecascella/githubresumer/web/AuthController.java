package it.davidecascella.githubresumer.web;

import it.davidecascella.githubresumer.service.GitHubOAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URI;
import java.util.UUID;

/** Flusso di login OAuth con GitHub. */
@RestController
@RequestMapping("/auth/github")
public class AuthController {

    private final GitHubOAuthService oAuthService;

    public AuthController(GitHubOAuthService oAuthService) {
        this.oAuthService = oAuthService;
    }

    /** L'app apre questo URL nel browser per avviare il login. */
    @GetMapping("/login")
    public RedirectView login() {
        String state = UUID.randomUUID().toString();
        return new RedirectView(oAuthService.buildAuthorizeUrl(state));
    }

    /** Callback chiamata da GitHub: completa il login e rimbalza il JWT all'app via deep link. */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam("code") String code) {
        String sessionJwt = oAuthService.completeLogin(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(oAuthService.buildAppRedirect(sessionJwt)))
                .build();
    }
}
