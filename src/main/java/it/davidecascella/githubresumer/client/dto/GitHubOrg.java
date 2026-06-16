package it.davidecascella.githubresumer.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubOrg(
        @JsonProperty("login") String login,
        @JsonProperty("id") Long id,
        @JsonProperty("name") String name,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("description") String description
) {
}
