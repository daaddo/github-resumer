package it.davidecascella.githubresumer.service;

import tools.jackson.databind.ObjectMapper;
import it.davidecascella.githubresumer.domain.TrackedRepository;
import it.davidecascella.githubresumer.domain.User;
import it.davidecascella.githubresumer.repository.TrackedRepositoryRepository;
import it.davidecascella.githubresumer.web.dto.InstallationEventPayload;
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
    private final InstallationService installationService;

    public WebhookService(ObjectMapper objectMapper,
                          TrackedRepositoryRepository trackedRepositoryRepository,
                          SummarizationService summarizationService,
                          FcmService fcmService,
                          InstallationService installationService) {
        this.objectMapper = objectMapper;
        this.trackedRepositoryRepository = trackedRepositoryRepository;
        this.summarizationService = summarizationService;
        this.fcmService = fcmService;
        this.installationService = installationService;
    }

    /**
     * Gestisce gli eventi "installation" e "installation_repositories" fuori dal thread della richiesta.
     * Su "deleted" rimuove l'installazione; per ogni altra azione riconcilia i repo tracciati con
     * lo stato corrente su GitHub (copre aggiunte, rimozioni e passaggio selected<->all).
     */
    @Async
    public void handleInstallationAsync(byte[] rawPayload) {
        try {
            InstallationEventPayload payload = objectMapper.readValue(rawPayload, InstallationEventPayload.class);
            if (payload.installation() == null || payload.installation().id() == null) {
                log.debug("Evento installation senza id: ignorato.");
                return;
            }
            long installationId = payload.installation().id();

            if ("deleted".equals(payload.action())) {
                installationService.removeInstallation(installationId);
            } else {
                String selection = payload.installation().repositorySelection();
                String accountLogin = payload.installation().account() != null
                        ? payload.installation().account().login() : null;
                installationService.reconcileInstallation(installationId, selection, accountLogin);
            }
        } catch (Exception e) {
            log.error("Errore nell'elaborazione dell'evento installation: {}", e.getMessage(), e);
        }
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
            // findByRepoFullNameFetchUser carica anche lo User (JOIN FETCH): qui siamo in un thread
            // @Async senza sessione, quindi non possiamo permetterci un accesso lazy più avanti.
            List<TrackedRepository> tracked = trackedRepositoryRepository.findByRepoFullNameFetchUser(repoFullName);
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
            log.info("summary : {}", summary);
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
