package it.davidecascella.githubresumer.service;

import it.davidecascella.githubresumer.client.GitHubApiClient;
import it.davidecascella.githubresumer.client.GitHubOAuthClient;
import it.davidecascella.githubresumer.client.dto.GitHubTokenResponse;
import it.davidecascella.githubresumer.client.dto.GitHubUser;
import it.davidecascella.githubresumer.config.GitHubProperties;
import it.davidecascella.githubresumer.domain.User;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Orchestrazione del flusso OAuth di GitHub. */
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
                + "&scope=" + enc("read:user")
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
        User user = userService.upsertFromGitHub(githubUser);
        return authCodeService.issue(user.getId(), codeChallenge);
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
     * Non usiamo un redirect 302 verso l'App Link: Android NON intercetta gli App Link sui
     * redirect lato server, quindi l'app non si aprirebbe. Restituiamo invece una pagina che
     * apre l'app via URI intent:// (con fallback al browser se l'app non è installata).
     */
    public String buildAppRedirectPage(String oneTimeCode) {
        return page(intentUrl("code", oneTimeCode));
    }

    /** Pagina HTML con un codice d'errore, così l'app non resta appesa nel browser dopo un login fallito. */
    public String buildAppErrorPage(String error) {
        return page(intentUrl("error", error));
    }

    /**
     * Costruisce l'URI intent:// che apre direttamente l'app per package, con scheme=https così
     * combacia con l'App Link verificato; se l'app non è installata Chrome usa browser_fallback_url.
     */
    private String intentUrl(String param, String value) {
        String appLink = properties.appRedirectUri();
        URI uri = URI.create(appLink);
        String scheme = uri.getScheme();
        String authorityAndPath = appLink.substring(scheme.length() + 3); // toglie "<scheme>://"
        String sep = appLink.contains("?") ? "&" : "?";
        String query = sep + param + "=" + enc(value);
        return "intent://" + authorityAndPath + query
                + "#Intent;scheme=" + scheme
                + ";package=" + properties.appPackage()
                + ";S.browser_fallback_url=" + enc(appLink + query)
                + ";end";
    }

    private String page(String intentUrl) {
        return "<!DOCTYPE html>\n"
                + "<html lang=\"it\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>Apertura app…</title></head>"
                + "<body style=\"font-family:sans-serif;text-align:center;padding:2rem\">"
                + "<p>Apertura dell'app in corso…</p>"
                + "<p><a href=\"" + htmlEscape(intentUrl) + "\">Tocca qui se l'app non si apre</a></p>"
                + "<script>window.location.replace('" + jsEscape(intentUrl) + "');</script>"
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
