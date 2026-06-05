package it.davidecascella.githubresumer.web.dto;

import jakarta.validation.constraints.NotBlank;

public record FcmTokenRequest(@NotBlank String fcmToken) {
}
