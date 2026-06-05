package it.davidecascella.githubresumer.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Risposta dell'endpoint OAuth di GitHub allo scambio del code. */
public record GitHubTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        String scope,
        String error,
        @JsonProperty("error_description") String errorDescription
) {
}
