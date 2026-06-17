package it.davidecascella.githubresumer.service;

import it.davidecascella.githubresumer.client.GitHubApiClient;
import it.davidecascella.githubresumer.client.GitHubOAuthClient;
import it.davidecascella.githubresumer.client.dto.GitHubTokenResponse;
import it.davidecascella.githubresumer.client.dto.GitHubUser;
import it.davidecascella.githubresumer.config.GitHubProperties;
import it.davidecascella.githubresumer.domain.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Orchestrazione del flusso OAuth di GitHub. */
@Slf4j
@Service
public class GitHubOAuthService {

    private final GitHubProperties properties;
    private final GitHubOAuthClient oAuthClient;
    private final GitHubApiClient apiClient;
    private final UserService userService;
    private final JwtService jwtService;
    private final AuthCodeService authCodeService;
    private final RefreshTokenService refreshTokenService;

    public GitHubOAuthService(GitHubProperties properties,
                              GitHubOAuthClient oAuthClient,
                              GitHubApiClient apiClient,
                              UserService userService,
                              JwtService jwtService,
                              AuthCodeService authCodeService,
                              RefreshTokenService refreshTokenService) {
        this.properties = properties;
        this.oAuthClient = oAuthClient;
        this.apiClient = apiClient;
        this.userService = userService;
        this.jwtService = jwtService;
        this.authCodeService = authCodeService;
        this.refreshTokenService = refreshTokenService;
    }

    /** URL a cui reindirizzare il browser per avviare il login. */
    public String buildAuthorizeUrl(String state) {
        String redirectUri = properties.callbackBaseUrl() + "/auth/github/callback";
        return "https://github.com/login/oauth/authorize"
                + "?client_id=" + enc(properties.clientId())
                + "&redirect_uri=" + enc(redirectUri)
                + "&scope=" + enc("read:user read:org public_repo")
                + "&state=" + enc(state);
    }

    /**
     * Completa il login: scambia il code di GitHub, recupera il profilo, fa upsert dell'utente
     * ed emette un codice usa-e-getta (NON il JWT) da consegnare all'app via App Link. Il codice
     * resta legato alla code challenge PKCE ricevuta all'avvio del login.
     */
    public String completeLogin(String code, String codeChallenge) {
        Map<String, String> body = new HashMap<>();
        body.put("client_id", properties.clientId());
        body.put("client_secret", properties.clientSecret());
        body.put("code", code);
        body.put("redirect_uri", properties.callbackBaseUrl() + "/auth/github/callback");

        GitHubTokenResponse token = oAuthClient.exchangeCodeForToken(body);
        if (token.accessToken() == null || token.accessToken().isBlank()) {

            throw new IllegalStateException("Scambio OAuth fallito: "
                    + (token.error() != null ? token.error() : "nessun access_token"));
        }

        GitHubUser githubUser = apiClient.getAuthenticatedUser("Bearer " + token.accessToken());
        User user = userService.upsertFromGitHub(githubUser, token.accessToken());
        return authCodeService.issue(user.getId(), codeChallenge);
    }

    /**
     * Identifica l'utente a partire dal code OAuth ricevuto durante l'installazione della GitHub App
     * ("Install & Authorize"): scambia il code, recupera il profilo GitHub e fa upsert dell'utente.
     * Non emette sessione: serve solo a sapere CHI sta installando, per legargli i repo.
     */
    public User authenticateUserFromCode(String code) {
        Map<String, String> body = new HashMap<>();
        body.put("client_id", properties.clientId());
        body.put("client_secret", properties.clientSecret());
        body.put("code", code);

        GitHubTokenResponse token = oAuthClient.exchangeCodeForToken(body);
        if (token.accessToken() == null || token.accessToken().isBlank()) {
            throw new IllegalStateException("Scambio OAuth (installazione) fallito: "
                    + (token.error() != null ? token.error() : "nessun access_token"));
        }
        GitHubUser githubUser = apiClient.getAuthenticatedUser("Bearer " + token.accessToken());
        return userService.upsertFromGitHub(githubUser, token.accessToken());
    }

    /**
     * Scambia il codice usa-e-getta con una sessione: consuma il codice (uso singolo) verificando
     * la prova PKCE (code_verifier) e, se valido, emette access token + refresh token per l'utente.
     */
    public SessionTokens exchangeCodeForSession(String oneTimeCode, String codeVerifier) {
        Long userId = authCodeService.consume(oneTimeCode, codeVerifier);
        User user = userService.getById(userId);
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getGithubUsername());
        String refreshToken = refreshTokenService.issue(user.getId());
        return new SessionTokens(accessToken, refreshToken);
    }

    /** Rinnova la sessione ruotando il refresh token ed emettendo un nuovo access token. */
    public SessionTokens refreshSession(String refreshToken) {
        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(refreshToken);
        User user = userService.getById(rotated.userId());
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getGithubUsername());
        return new SessionTokens(accessToken, rotated.refreshToken());
    }

    /** Revoca il refresh token (logout). */
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    /**
     * Pagina HTML che rimbalza il codice usa-e-getta all'app Android dopo il callback OAuth.
     * Il ritorno usa un custom scheme (es. githubresumer://auth/callback): dalla Custom Tab l'OS
     * consegna l'URL all'app che possiede l'intent-filter. Evita l'intent://...;package=<app stessa>,
     * che dentro una Custom Tab veniva bloccato (anti-loop) e degradava nel Play Store (market://).
     */
    public String buildAppRedirectPage(String oneTimeCode) {
        return page(appUrl("code", oneTimeCode));
    }

    /** Pagina HTML con un codice d'errore, così l'app non resta appesa nel browser dopo un login fallito. */
    public String buildAppErrorPage(String error) {
        return page(appUrl("error", error));
    }

    /** Pagina HTML che, dopo l'installazione della App, rimbalza all'app segnalando quanti repo sono stati tracciati. */
    public String buildAppInstalledPage(int trackedCount) {
        String base = properties.appRedirectUri();
        String sep = base.contains("?") ? "&" : "?";
        String url = base + sep + "installed=true&count=" + enc(String.valueOf(trackedCount));
        log.info("Redirect all'app dopo installazione: url={}", url);
        return page(url);
    }

    /** Costruisce l'URL custom-scheme che riapre l'app col codice usa-e-getta (o con l'errore). */
    private String appUrl(String param, String value) {
        String base = properties.appRedirectUri();
        String sep = base.contains("?") ? "&" : "?";
        String url = base + sep + param + "=" + enc(value);
        log.info("Redirect all'app: appRedirectUri={}, url={}", base, url);
        return url;
    }

    private String page(String appUrl) {
        return "<!DOCTYPE html>\n"
                + "<html lang=\"it\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>Apertura app…</title></head>"
                + "<body style=\"font-family:sans-serif;text-align:center;padding:2rem\">"
                + "<p>Apertura dell'app in corso…</p>"
                + "<p><a href=\"" + htmlEscape(appUrl) + "\" "
                + "style=\"display:inline-block;padding:0.9rem 1.6rem;background:#24292f;color:#fff;"
                + "text-decoration:none;border-radius:8px;font-size:1.1rem;font-weight:600\">"
                + "Apri GitHub Resumer</a></p>"
                + "<p style=\"color:#666;font-size:0.9rem\">Se l'app non si apre da sola, tocca il pulsante qui sopra.</p>"
                + "<script>window.location.replace('" + jsEscape(appUrl) + "');</script>"
                + "</body></html>";
    }

    private String htmlEscape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String jsEscape(String value) {
        return value.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("<", "\\x3C"); // evita la chiusura anticipata di </script>
    }

    private String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
