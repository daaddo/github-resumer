package it.davidecascella.githubresumer.web.dto;

import it.davidecascella.githubresumer.domain.Role;
import it.davidecascella.githubresumer.domain.User;

import java.time.Instant;

/** Vista sintetica di un utente per il pannello di amministrazione. */
public record AdminUserResponse(
        Long id,
        String githubUsername,
        String avatarUrl,
        Role role,
        Instant createdAt
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getGithubUsername(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getCreatedAt());
    }
}
