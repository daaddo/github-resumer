package it.davidecascella.githubresumer.service;

import it.davidecascella.githubresumer.client.dto.GitHubUser;
import it.davidecascella.githubresumer.domain.TrackedRepository;
import it.davidecascella.githubresumer.domain.User;
import it.davidecascella.githubresumer.repository.TrackedRepositoryRepository;
import it.davidecascella.githubresumer.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TrackedRepositoryRepository trackedRepositoryRepository;

    public UserService(UserRepository userRepository, TrackedRepositoryRepository trackedRepositoryRepository) {
        this.userRepository = userRepository;
        this.trackedRepositoryRepository = trackedRepositoryRepository;
    }

    /** Crea o aggiorna l'utente a partire dal profilo GitHub ottenuto via OAuth (match per account id). */
    @Transactional
    public User upsertFromGitHub(GitHubUser githubUser) {
        User user = userRepository.findByGithubAccountId(githubUser.id())
                .orElseGet(User::new);
        user.setGithubAccountId(githubUser.id());
        user.setGithubUsername(githubUser.login());
        user.setAvatarUrl(githubUser.avatarUrl());
        return userRepository.save(user);
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utente non trovato: " + id));
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
}
