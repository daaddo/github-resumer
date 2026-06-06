package it.davidecascella.githubresumer.service;

import it.davidecascella.githubresumer.client.GitHubApiClient;
import it.davidecascella.githubresumer.client.GitHubOAuthClient;
import it.davidecascella.githubresumer.client.dto.GitHubTokenResponse;
import it.davidecascella.githubresumer.client.dto.GitHubUser;
import it.davidecascella.githubresumer.config.GitHubProperties;
import it.davidecascella.githubresumer.domain.User;
import org.springframework.stereotype.Service;

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
     * ed emette un codice usa-e-getta (NON il JWT) da consegnare all'app via App Link.
     */
    public String completeLogin(String code) {
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
        return authCodeService.issue(user.getId());
    }

    /**
     * Scambia il codice usa-e-getta con una sessione: consuma il codice (uso singolo) e,
     * se valido, emette access token + refresh token per l'utente collegato.
     */
    public SessionTokens exchangeCodeForSession(String oneTimeCode) {
        Long userId = authCodeService.consume(oneTimeCode);
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

    /** App Link a cui rimbalzare il codice usa-e-getta dopo il callback OAuth. */
    public String buildAppRedirect(String oneTimeCode) {
        String sep = properties.appRedirectUri().contains("?") ? "&" : "?";
        return properties.appRedirectUri() + sep + "code=" + enc(oneTimeCode);
    }

    private String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
