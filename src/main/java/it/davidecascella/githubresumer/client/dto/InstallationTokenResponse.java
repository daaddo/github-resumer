package it.davidecascella.githubresumer.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Risposta di POST /app/installations/{id}/access_tokens. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InstallationTokenResponse(
        String token,
        @JsonProperty("expires_at") String expiresAt
) {
}
