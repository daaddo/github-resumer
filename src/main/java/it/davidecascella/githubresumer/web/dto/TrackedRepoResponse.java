package it.davidecascella.githubresumer.web.dto;

import it.davidecascella.githubresumer.domain.TrackedRepository;

public record TrackedRepoResponse(Long id, String repoFullName) {
    public static TrackedRepoResponse from(TrackedRepository entity) {
        return new TrackedRepoResponse(entity.getId(), entity.getRepoFullName());
    }
}
