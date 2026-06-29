package it.davidecascella.githubresumer.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Body del PATCH che accende/spegne il flag {@code tracked} di un repo già seguito dall'utente.
 * {@code tracked = true} -> l'utente riceve il digest IA; {@code false} -> repo silenziato.
 */
public record SetTrackedRequest(
        @NotBlank
        @Pattern(regexp = "^[^/\\s]+/[^/\\s]+$", message = "repoFullName deve essere nel formato owner/repo")
        String repoFullName,

        @NotNull
        Boolean tracked
) {
}
