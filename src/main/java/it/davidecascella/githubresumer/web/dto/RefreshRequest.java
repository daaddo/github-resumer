package it.davidecascella.githubresumer.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Corpo per il rinnovo e il logout: il refresh token corrente. */
public record RefreshRequest(@NotBlank String refreshToken) {
}
