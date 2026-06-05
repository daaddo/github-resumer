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

    public GitHubOAuthService(GitHubProperties properties,
                              GitHubOAuthClient oAuthClient,
                              GitHubApiClient apiClient,
                              UserService userService,
                              JwtService jwtService) {
        this.properties = properties;
        this.oAuthClient = oAuthClient;
        this.apiClient = apiClient;
        this.userService = userService;
        this.jwtService = jwtService;
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
     * Completa il login: scambia il code, recupera il profilo, fa upsert dell'utente
     * ed emette il JWT di sessione da consegnare all'app.
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
        return jwtService.issueSessionToken(user.getId(), user.getGithubUsername());
    }

    /** Deep link dell'app con il JWT, usato per rimbalzare la sessione al client dopo il callback. */
    public String buildAppRedirect(String sessionJwt) {
        String sep = properties.appRedirectUri().contains("?") ? "&" : "?";
        return properties.appRedirectUri() + sep + "token=" + enc(sessionJwt);
    }

    private String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
