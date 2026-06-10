package it.davidecascella.githubresumer.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Corpo della richiesta di scambio: il codice usa-e-getta ricevuto nell'App Link
 * piu' il code_verifier PKCE in chiaro, di cui all'avvio del login era stata pubblicata solo la challenge.
 * Il verifier rispetta il formato RFC 7636: 43-128 caratteri dell'insieme [A-Za-z0-9-._~].
 */
public record ExchangeRequest(
        @NotBlank String code,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9\\-._~]{43,128}") String codeVerifier
) {
}
