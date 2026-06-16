package it.davidecascella.githubresumer.web.dto;

import it.davidecascella.githubresumer.client.dto.GitHubOrg;
import it.davidecascella.githubresumer.client.dto.GitHubRepo;

import java.util.List;

public record OrgWithReposResponse(
        String login,
        String name,
        String avatarUrl,
        String description,
        List<RepoSummary> repos
) {

    public record RepoSummary(
            Long id,
            String name,
            String fullName,
            boolean privateRepo,
            String htmlUrl,
            String description,
            String language,
            int stars,
            int forks,
            String updatedAt
    ) {
        public static RepoSummary from(GitHubRepo r) {
            return new RepoSummary(
                    r.id(), r.name(), r.fullName(), r.privateRepo(),
                    r.htmlUrl(), r.description(), r.language(),
                    r.stars(), r.forks(), r.updatedAt()
            );
        }
    }

    public static OrgWithReposResponse from(GitHubOrg org, List<GitHubRepo> repos) {
        return new OrgWithReposResponse(
                org.login(),
                org.name(),
                org.avatarUrl(),
                org.description(),
                repos.stream().map(RepoSummary::from).toList()
        );
    }
}
