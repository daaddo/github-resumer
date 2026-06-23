package it.davidecascella.githubresumer.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Sottoinsieme del payload dell'evento "push" di GitHub che ci interessa. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PushEventPayload(
        String ref,
        @JsonProperty("after") String after,
        Repository repository,
        List<Commit> commits
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Repository(@JsonProperty("full_name") String fullName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Commit(String id, String message, Author author) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Author(String name, String email) {
    }

    /** Estrae "main" da "refs/heads/main". */
    public String branch() {
        if (ref == null) {
            return null;
        }
        int idx = ref.lastIndexOf('/');
        return idx >= 0 ? ref.substring(idx + 1) : ref;
    }

    /**
     * SHA del commit di testa del push: usa il campo {@code after} (sempre presente negli eventi
     * push), con fallback sull'ultimo commit della lista se per qualche motivo mancasse.
     * Ritorna null solo se non c'è alcun commit identificabile.
     */
    public String headCommitId() {
        if (after != null && !after.isBlank()) {
            return after;
        }
        if (commits != null && !commits.isEmpty()) {
            return commits.get(commits.size() - 1).id();
        }
        return null;
    }
}
