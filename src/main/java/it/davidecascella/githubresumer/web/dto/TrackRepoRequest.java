package it.davidecascella.githubresumer.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Il nome deve essere nel formato "owner/repo". */
public record TrackRepoRequest(
        @NotBlank
        @Pattern(regexp = "^[^/\\s]+/[^/\\s]+$", message = "repoFullName deve essere nel formato owner/repo")
        String repoFullName
) {
}
