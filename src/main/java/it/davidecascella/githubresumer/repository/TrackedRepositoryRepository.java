package it.davidecascella.githubresumer.repository;

import it.davidecascella.githubresumer.domain.TrackedRepository;
import it.davidecascella.githubresumer.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrackedRepositoryRepository extends JpaRepository<TrackedRepository, Long> {

    List<TrackedRepository> findByUser(User user);

    List<TrackedRepository> findByUserId(Long userId);

    /** Usata dal webhook per il filtro "Buttafuori": tutti gli utenti che tracciano questo repo. */
    List<TrackedRepository> findByRepoFullName(String repoFullName);

    Optional<TrackedRepository> findByUserIdAndRepoFullName(Long userId, String repoFullName);

    /** Tracked repo provenienti da una specifica installazione (per la riconciliazione). */
    List<TrackedRepository> findByInstallationId(Long installationId);

    boolean existsByUserIdAndRepoFullName(Long userId, String repoFullName);

    void deleteByUserIdAndRepoFullName(Long userId, String repoFullName);
}
