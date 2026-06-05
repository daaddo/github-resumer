package it.davidecascella.githubresumer.service;

import tools.jackson.databind.ObjectMapper;
import it.davidecascella.githubresumer.domain.TrackedRepository;
import it.davidecascella.githubresumer.domain.User;
import it.davidecascella.githubresumer.repository.TrackedRepositoryRepository;
import it.davidecascella.githubresumer.web.dto.PushEventPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Elaborazione degli eventi del webhook.
 * Tutta la parte lenta (parsing, LLM, FCM) gira in modo ASINCRONO: il controller risponde
 * subito 200 OK così GitHub non sospende il webhook (Regola del Buttafuori).
 */
@Slf4j
@Service
public class WebhookService {

    private final ObjectMapper objectMapper;
    private final TrackedRepositoryRepository trackedRepositoryRepository;
    private final SummarizationService summarizationService;
    private final FcmService fcmService;

    public WebhookService(ObjectMapper objectMapper,
                          TrackedRepositoryRepository trackedRepositoryRepository,
                          SummarizationService summarizationService,
                          FcmService fcmService) {
        this.objectMapper = objectMapper;
        this.trackedRepositoryRepository = trackedRepositoryRepository;
        this.summarizationService = summarizationService;
        this.fcmService = fcmService;
    }

    /** Gestisce un evento "push" fuori dal thread della richiesta. */
    @Async
    public void handlePushAsync(byte[] rawPayload) {
        try {
            PushEventPayload payload = objectMapper.readValue(rawPayload, PushEventPayload.class);

            if (payload.repository() == null || payload.repository().fullName() == null) {
                log.debug("Push senza repository.full_name: ignorato.");
                return;
            }
            String repoFullName = payload.repository().fullName();

            // Regola del Buttafuori: procedo solo se almeno un utente traccia questo repo.
            List<TrackedRepository> tracked = trackedRepositoryRepository.findByRepoFullName(repoFullName);
            if (tracked.isEmpty()) {
                log.debug("Repo {} non tracciato da alcun utente: scartato.", repoFullName);
                return;
            }

            List<String> messages = payload.commits() == null ? List.of()
                    : payload.commits().stream()
                    .map(PushEventPayload.Commit::message)
                    .filter(m -> m != null && !m.isBlank())
                    .toList();
            if (messages.isEmpty()) {
                log.debug("Push su {} senza commit (es. creazione/cancellazione branch): ignorato.", repoFullName);
                return;
            }

            String summary = summarizationService.summarizePush(repoFullName, payload.branch(), messages);

            for (TrackedRepository repo : tracked) {
                User user = repo.getUser();
                fcmService.sendCommitSummary(user.getFcmToken(), repoFullName, summary);
            }
        } catch (Exception e) {
            // Non rilanciamo: l'errore non deve mai propagarsi al webhook.
            log.error("Errore nell'elaborazione del push: {}", e.getMessage(), e);
        }
    }
}
