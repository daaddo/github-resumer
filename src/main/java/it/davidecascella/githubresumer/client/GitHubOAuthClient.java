package it.davidecascella.githubresumer.client;

import it.davidecascella.githubresumer.client.dto.GitHubTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/** Client per il flusso OAuth (host github.com, non api.github.com). */
@FeignClient(name = "githubOAuthClient", url = "https://github.com")
public interface GitHubOAuthClient {

    /**
     * Scambia il code OAuth con un access token utente.
     * Il body è una mappa con client_id, client_secret, code, redirect_uri.
     */
    @PostMapping(
            value = "/login/oauth/access_token",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    GitHubTokenResponse exchangeCodeForToken(@RequestBody Map<String, String> body);
}
