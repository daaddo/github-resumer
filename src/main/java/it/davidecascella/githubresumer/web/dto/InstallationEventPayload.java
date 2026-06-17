package it.davidecascella.githubresumer.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Sottoinsieme dei payload "installation" e "installation_repositories" di GitHub.
 * Entrambi contengono un oggetto "installation" con l'id: ci basta quello (+ l'azione)
 * per riconciliare i repo dell'installazione.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InstallationEventPayload(
        String action,
        Installation installation
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Installation(
            Long id,
            @JsonProperty("repository_selection") String repositorySelection,
            Account account
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(String login) {
    }
}
