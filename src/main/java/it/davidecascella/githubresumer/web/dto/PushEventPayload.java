package it.davidecascella.githubresumer.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Sottoinsieme del payload dell'evento "push" di GitHub che ci interessa. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PushEventPayload(
        String ref,
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
}
