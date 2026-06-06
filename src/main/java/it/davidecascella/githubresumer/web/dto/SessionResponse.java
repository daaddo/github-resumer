package it.davidecascella.githubresumer.web.dto;

/**
 * Risposta di login/refresh: l'access token (Bearer su /api/**, breve) e il refresh token
 * (da conservare e usare su /auth/refresh quando l'access token scade).
 */
public record SessionResponse(String accessToken, String refreshToken) {
}
