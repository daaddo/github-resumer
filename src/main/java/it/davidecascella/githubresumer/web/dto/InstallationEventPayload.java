package it.davidecascella.githubresumer.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Sottoinsieme dei payload "installation" e "installation_repositories" di GitHub.
 *
 * <p>Oltre all'installazione (id + selezione repo) ci interessano due attori, usati al "created"
 * per legare l'installazione all'utente giusto:
 * <ul>
 *   <li>{@code requester}: chi ha RICHIESTO l'installazione (valorizzato solo nel flusso
 *       richiesta&rarr;approvazione su org; {@code null} nelle installazioni dirette);</li>
 *   <li>{@code sender}: chi ha scatenato l'evento (l'installatore diretto, o l'owner che approva).</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InstallationEventPayload(
        String action,
        Installation installation,
        Account requester,
        Account sender
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Installation(
            Long id,
            @JsonProperty("repository_selection") String repositorySelection,
            Account account
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(Long id, String login) {
    }
}
