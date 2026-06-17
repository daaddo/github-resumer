package it.davidecascella.githubresumer.service;

import it.davidecascella.githubresumer.client.GitHubApiClient;
import it.davidecascella.githubresumer.client.dto.GitHubOrg;
import it.davidecascella.githubresumer.client.dto.GitHubRepo;
import it.davidecascella.githubresumer.client.dto.GitHubUser;
import it.davidecascella.githubresumer.domain.TrackedRepository;
import it.davidecascella.githubresumer.domain.User;
import it.davidecascella.githubresumer.repository.TrackedRepositoryRepository;
import it.davidecascella.githubresumer.repository.UserRepository;
import it.davidecascella.githubresumer.web.dto.OrgWithReposResponse;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class UserService {

    private static final int PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final TrackedRepositoryRepository trackedRepositoryRepository;
    private final GitHubApiClient gitHubApiClient;
    private final AesTokenEncryptor encryptor;

    public UserService(UserRepository userRepository,
                       TrackedRepositoryRepository trackedRepositoryRepository,
                       GitHubApiClient gitHubApiClient,
                       AesTokenEncryptor encryptor) {
        this.userRepository = userRepository;
        this.trackedRepositoryRepository = trackedRepositoryRepository;
        this.gitHubApiClient = gitHubApiClient;
        this.encryptor = encryptor;
    }

    /** Crea o aggiorna l'utente a partire dal profilo GitHub ottenuto via OAuth (match per account id). */
    @Transactional
    public User upsertFromGitHub(GitHubUser githubUser, String githubAccessToken) {
        User user = userRepository.findByGithubAccountId(githubUser.id())
                .orElseGet(User::new);
        user.setGithubAccountId(githubUser.id());
        user.setGithubUsername(githubUser.login());
        user.setAvatarUrl(githubUser.avatarUrl());
        user.setGithubAccessToken(encryptor.encrypt(githubAccessToken));
        return userRepository.save(user);
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Sessione non più valida: effettua nuovamente il login."));
    }

    @Transactional
    public void updateFcmToken(Long userId, String fcmToken) {
        User user = getById(userId);
        user.setFcmToken(fcmToken);
        userRepository.save(user);
    }

    public List<TrackedRepository> listTrackedRepos(Long userId) {
        return trackedRepositoryRepository.findByUserId(userId);
    }

    @Transactional
    public TrackedRepository trackRepo(Long userId, String repoFullName) {
        return trackedRepositoryRepository.findByUserIdAndRepoFullName(userId, repoFullName)
                .orElseGet(() -> {
                    User user = getById(userId);
                    return trackedRepositoryRepository.save(
                            TrackedRepository.builder().user(user).repoFullName(repoFullName).build());
                });
    }

    @Transactional
    public void untrackRepo(Long userId, String repoFullName) {
        trackedRepositoryRepository.deleteByUserIdAndRepoFullName(userId, repoFullName);
    }

    /**
     * Ritorna le organizzazioni dell'utente con i rispettivi repository,
     * iterando tutte le pagine di ciascuna risorsa GitHub.
     */
    public List<OrgWithReposResponse> getGithubOrgsWithRepos(Long userId) {
        User user = getById(userId);
        if (user.getGithubAccessToken() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Token GitHub non disponibile: effettua nuovamente il login.");
        }
        String bearer = "Bearer " + encryptor.decrypt(user.getGithubAccessToken());

        List<GitHubOrg> orgs = fetchAllPages(
                (page) -> gitHubApiClient.getUserOrgs(bearer, PAGE_SIZE, page));
        log.info("Utente {}: GitHub ha restituito {} organizzazioni.", userId, orgs.size());

        List<OrgWithReposResponse> result = new ArrayList<>();
        for (GitHubOrg org : orgs) {
            try {
                List<GitHubRepo> repos = fetchAllPages(
                        (page) -> gitHubApiClient.getOrgRepos(org.login(), bearer, PAGE_SIZE, page, "all"));
                result.add(OrgWithReposResponse.from(org, repos));
            } catch (FeignException.NotFound e) {
                log.warn("Repos dell'org '{}' non accessibili (GitHub 404) per l'utente {}: " +
                        "la includo senza repository (probabile restrizione OAuth app lato org).",
                        org.login(), userId);
                result.add(OrgWithReposResponse.from(org, List.of()));
            }
        }
        return result;
    }

    private <T> List<T> fetchAllPages(PageFetcher<T> fetcher) {
        List<T> all = new ArrayList<>();
        int page = 1;
        List<T> batch;
        do {
            batch = fetcher.fetch(page++);
            all.addAll(batch);
        } while (batch.size() == PAGE_SIZE);
        return all;
    }

    @FunctionalInterface
    private interface PageFetcher<T> {
        List<T> fetch(int page);
    }
}
