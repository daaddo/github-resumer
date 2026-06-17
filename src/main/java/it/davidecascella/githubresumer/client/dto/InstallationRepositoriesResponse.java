package it.davidecascella.githubresumer.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Risposta di GET /installation/repositories: i repo accessibili a una installazione. */
public record InstallationRepositoriesResponse(
        @JsonProperty("total_count") int totalCount,
        @JsonProperty("repositories") List<GitHubRepo> repositories
) {
}
