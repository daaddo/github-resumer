package it.davidecascella.githubresumer.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Corpo della richiesta di scambio: il codice usa-e-getta ricevuto nell'App Link. */
public record ExchangeRequest(@NotBlank String code) {
}
