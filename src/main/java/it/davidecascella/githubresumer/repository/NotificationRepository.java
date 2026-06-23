package it.davidecascella.githubresumer.repository;

import it.davidecascella.githubresumer.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Storico paginato di un utente, dal più recente al più vecchio. */
    Page<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    /** Come sopra, ma filtrato su un singolo repository (ricerca per repository). */
    Page<Notification> findByUserIdAndRepoFullNameOrderByCreatedAtDescIdDesc(
            Long userId, String repoFullName, Pageable pageable);

    /** Idempotenza: una notifica è identificata da utente + repo + titolo + commit. */
    boolean existsByUserIdAndRepoFullNameAndTitleAndCommitId(
            Long userId, String repoFullName, String title, String commitId);
}
