package it.davidecascella.githubresumer.repository;

import it.davidecascella.githubresumer.domain.TrackedRepository;
import it.davidecascella.githubresumer.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrackedRepositoryRepository extends JpaRepository<TrackedRepository, Long> {

    List<TrackedRepository> findByUser(User user);

    List<TrackedRepository> findByUserId(Long userId);

    /** Usata dal webhook per il filtro "Buttafuori": tutti gli utenti che tracciano questo repo. */
    List<TrackedRepository> findByRepoFullName(String repoFullName);

    /**
     * Come {@link #findByRepoFullName}, ma carica anche lo User nella stessa query (JOIN FETCH).
     * Serve al webhook: gira in un thread @Async senza sessione aperta (open-in-view=false), quindi
     * lo User va materializzato subito, altrimenti l'accesso lazy a user.fcmToken esplode.
     */
    @Query("select t from TrackedRepository t join fetch t.user where t.repoFullName = :repoFullName")
    List<TrackedRepository> findByRepoFullNameFetchUser(@Param("repoFullName") String repoFullName);

    /**
     * Come {@link #findByRepoFullNameFetchUser}, ma solo le righe con {@code tracked = true}.
     * È il gate del digest: il webhook produce e recapita il riassunto solo agli utenti che
     * hanno attivato il tracking per quel repo (gli altri lo "seguono" ma sono silenziati).
     */
    @Query("select t from TrackedRepository t join fetch t.user " +
            "where t.repoFullName = :repoFullName and t.tracked = true")
    List<TrackedRepository> findTrackedByRepoFullNameFetchUser(@Param("repoFullName") String repoFullName);

    Optional<TrackedRepository> findByUserIdAndRepoFullName(Long userId, String repoFullName);

    /** Tracked repo provenienti da una specifica installazione (per la riconciliazione). */
    List<TrackedRepository> findByInstallationId(Long installationId);

    boolean existsByUserIdAndRepoFullName(Long userId, String repoFullName);

    void deleteByUserIdAndRepoFullName(Long userId, String repoFullName);
}
