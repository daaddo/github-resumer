package it.davidecascella.githubresumer.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubRepo(
        @JsonProperty("id") Long id,
        @JsonProperty("name") String name,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("private") boolean privateRepo,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("description") String description,
        @JsonProperty("language") String language,
        @JsonProperty("default_branch") String defaultBranch,
        @JsonProperty("stargazers_count") int stars,
        @JsonProperty("forks_count") int forks,
        @JsonProperty("updated_at") String updatedAt
) {
}
