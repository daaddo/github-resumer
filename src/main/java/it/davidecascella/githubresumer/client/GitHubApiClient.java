package it.davidecascella.githubresumer.client;

import it.davidecascella.githubresumer.client.dto.GitHubUser;
import it.davidecascella.githubresumer.client.dto.InstallationTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/** Client per le REST API di GitHub (api.github.com). */
@FeignClient(name = "githubApiClient", url = "https://api.github.com")
public interface GitHubApiClient {

    /** Profilo dell'utente proprietario dell'access token OAuth. */
    @GetMapping("/user")
    GitHubUser getAuthenticatedUser(@RequestHeader("Authorization") String bearerToken);

    /**
     * Crea un installation access token a partire dal JWT della GitHub App.
     * @param bearerAppJwt header "Bearer <app-jwt>"
     */
    @PostMapping("/app/installations/{installationId}/access_tokens")
    InstallationTokenResponse createInstallationToken(
            @PathVariable("installationId") long installationId,
            @RequestHeader("Authorization") String bearerAppJwt
    );
}
