package it.davidecascella.githubresumer.service;

/** Coppia di token di sessione: access token (JWT breve) + refresh token (lungo, revocabile). */
public record SessionTokens(String accessToken, String refreshToken) {
}
