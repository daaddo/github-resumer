package it.davidecascella.githubresumer.client;

import it.davidecascella.githubresumer.client.dto.GitHubOrg;
import it.davidecascella.githubresumer.client.dto.GitHubRepo;
import it.davidecascella.githubresumer.client.dto.GitHubUser;
import it.davidecascella.githubresumer.client.dto.InstallationRepositoriesResponse;
import it.davidecascella.githubresumer.client.dto.InstallationTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** Client per le REST API di GitHub (api.github.com). */
@FeignClient(name = "githubApiClient", url = "https://api.github.com")
public interface GitHubApiClient {

    /** Profilo dell'utente proprietario dell'access token OAuth. */
    @GetMapping("/user")
    GitHubUser getAuthenticatedUser(@RequestHeader("Authorization") String bearerToken);

    /** Organizzazioni a cui appartiene l'utente autenticato (richiede scope read:org). */
    @GetMapping("/user/orgs")
    List<GitHubOrg> getUserOrgs(
            @RequestHeader("Authorization") String bearerToken,
            @RequestParam("per_page") int perPage,
            @RequestParam("page") int page
    );

    /** Repository di un'organizzazione (richiede scope public_repo o repo). */
    @GetMapping("/orgs/{org}/repos")
    List<GitHubRepo> getOrgRepos(
            @PathVariable("org") String org,
            @RequestHeader("Authorization") String bearerToken,
            @RequestParam("per_page") int perPage,
            @RequestParam("page") int page,
            @RequestParam("type") String type
    );

    /**
     * Repository accessibili a una installazione della GitHub App (pubblici e privati inclusi
     * nell'installazione). Richiede un installation access token come Bearer.
     */
    @GetMapping("/installation/repositories")
    InstallationRepositoriesResponse getInstallationRepositories(
            @RequestHeader("Authorization") String bearerInstallationToken,
            @RequestParam("per_page") int perPage,
            @RequestParam("page") int page
    );

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
