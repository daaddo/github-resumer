package it.davidecascella.githubresumer.repository;

import it.davidecascella.githubresumer.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Storico paginato di un utente, dal più recente al più vecchio. */
    Page<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    /** Come sopra, ma filtrato su un singolo repository (ricerca per repository). */
    Page<Notification> findByUserIdAndRepoFullNameOrderByCreatedAtDescIdDesc(
            Long userId, String repoFullName, Pageable pageable);

    /** Idempotenza: una notifica è identificata da utente + repo + titolo + commit. */
    boolean existsByUserIdAndRepoFullNameAndTitleAndCommitId(
            Long userId, String repoFullName, String title, String commitId);

    /**
     * Cancella la notifica solo se appartiene all'utente: il vincolo sull'owner è nella query,
     * quindi un id di un altro utente non elimina nulla. Ritorna le righe cancellate (0 o 1).
     * {@code @Transactional} qui così il metodo è autosufficiente (una delete derivata richiede
     * una transazione attiva).
     */
    @Transactional
    long deleteByIdAndUserId(Long id, Long userId);
}
