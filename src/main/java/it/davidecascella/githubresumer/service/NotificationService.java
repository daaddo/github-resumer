package it.davidecascella.githubresumer.service;

import it.davidecascella.githubresumer.domain.Notification;
import it.davidecascella.githubresumer.domain.User;
import it.davidecascella.githubresumer.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class NotificationService {

    /**
     * Limite prudenziale di caratteri per il titolo: la colonna è TEXT (max 65.535 byte) e in
     * utf8mb4 un carattere può pesare fino a 4 byte, quindi tronchiamo ben sotto la capacità
     * per non far fallire l'insert su input anomali.
     */
    private static final int MAX_TITLE_LENGTH = 16_000;

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Salva una notifica per l'utente, in modo idempotente sull'identità
     * (utente, repo, titolo, commit). Se esiste già non fa nulla.
     * La deduplica è applicativa (niente indice univoco sull'intera riga): nei rari re-delivery
     * del webhook il controllo {@code existsBy} basta, dato che lo stesso push viene elaborato
     * in modo sequenziale dal thread @Async. Apre la propria transazione.
     */
    @Transactional
    public void record(User recipient, String repoFullName, String title, String commitId) {
        String safeTitle = truncate(title, MAX_TITLE_LENGTH);

        if (notificationRepository.existsByUserIdAndRepoFullNameAndTitleAndCommitId(
                recipient.getId(), repoFullName, safeTitle, commitId)) {
            log.debug("Notifica già presente per utente {} repo {} commit {}: salto.",
                    recipient.getId(), repoFullName, commitId);
            return;
        }
        notificationRepository.save(Notification.builder()
                .user(recipient)
                .repoFullName(repoFullName)
                .title(safeTitle)
                .commitId(commitId)
                .build());
    }

    /**
     * Storico paginato e cronologico (più recenti prima) per l'utente.
     * Se {@code repoFullName} è valorizzato, filtra le notifiche di quel solo repository
     * (filtro servito dall'indice (user_id, repo_full_name)).
     */
    @Transactional(readOnly = true)
    public Page<Notification> list(Long userId, String repoFullName, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (StringUtils.hasText(repoFullName)) {
            return notificationRepository
                    .findByUserIdAndRepoFullNameOrderByCreatedAtDescIdDesc(userId, repoFullName, pageable);
        }
        return notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
