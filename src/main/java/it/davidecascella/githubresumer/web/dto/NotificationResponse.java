package it.davidecascella.githubresumer.web.dto;

import it.davidecascella.githubresumer.domain.Notification;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        String repoFullName,
        String title,
        String description,
        String commitId,
        Instant createdAt
) {
    public static NotificationResponse from(Notification entity) {
        return new NotificationResponse(
                entity.getId(),
                entity.getRepoFullName(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getCommitId(),
                entity.getCreatedAt());
    }
}
