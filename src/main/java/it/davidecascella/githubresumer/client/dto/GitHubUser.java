package it.davidecascella.githubresumer.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Profilo utente restituito da GET https://api.github.com/user. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubUser(
        Long id,
        String login,
        String name,
        @JsonProperty("avatar_url") String avatarUrl
) {
}
