package it.davidecascella.githubresumer.service;

import tools.jackson.databind.ObjectMapper;
import it.davidecascella.githubresumer.client.GitHubApiClient;
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

    /** Tetto ai caratteri di diff scaricati: protegge da diff giganti prima ancora dell'LLM. */
    private static final int MAX_DIFF_CHARS = 60_000;

    private final ObjectMapper objectMapper;
    private final TrackedRepositoryRepository trackedRepositoryRepository;
    private final SummarizationService summarizationService;
    private final FcmService fcmService;
    private final InstallationService installationService;
    private final NotificationService notificationService;
    private final DeviceTokenService deviceTokenService;
    private final GitHubAppService gitHubAppService;
    private final GitHubApiClient gitHubApiClient;

    public WebhookService(ObjectMapper objectMapper,
                          TrackedRepositoryRepository trackedRepositoryRepository,
                          SummarizationService summarizationService,
                          FcmService fcmService,
                          InstallationService installationService,
                          NotificationService notificationService,
                          DeviceTokenService deviceTokenService,
                          GitHubAppService gitHubAppService,
                          GitHubApiClient gitHubApiClient) {
        this.objectMapper = objectMapper;
        this.trackedRepositoryRepository = trackedRepositoryRepository;
        this.summarizationService = summarizationService;
        this.fcmService = fcmService;
        this.installationService = installationService;
        this.notificationService = notificationService;
        this.deviceTokenService = deviceTokenService;
        this.gitHubAppService = gitHubAppService;
        this.gitHubApiClient = gitHubApiClient;
    }

    /**
     * Gestisce gli eventi "installation" e "installation_repositories" fuori dal thread della richiesta.
     * <ul>
     *   <li>"created": crea l'installazione legandola all'utente (requester, o sender se diretta),
     *       rifiutandola se l'utente non è registrato;</li>
     *   <li>"deleted": rimuove l'installazione;</li>
     *   <li>ogni altra azione (aggiunta/rimozione repo, suspend/unsuspend): riconcilia i repo
     *       tracciati con lo stato corrente su GitHub.</li>
     * </ul>
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
            String selection = payload.installation().repositorySelection();
            String accountLogin = payload.installation().account() != null
                    ? payload.installation().account().login() : null;

            if ("created".equals(payload.action())) {
                Long requesterId = payload.requester() != null ? payload.requester().id() : null;
                Long senderId = payload.sender() != null ? payload.sender().id() : null;
                installationService.onInstallationCreated(
                        installationId, selection, accountLogin, requesterId, senderId);
            } else if ("deleted".equals(payload.action())) {
                installationService.removeInstallation(installationId);
            } else {
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

            // Regola del Buttafuori: procedo solo se almeno un utente ha tracked = true su questo
            // repo (chi lo "segue" soltanto è silenziato). findTrackedByRepoFullNameFetchUser carica
            // anche lo User (JOIN FETCH): qui siamo in un thread @Async senza sessione, quindi non
            // possiamo permetterci un accesso lazy più avanti.
            List<TrackedRepository> tracked =
                    trackedRepositoryRepository.findTrackedByRepoFullNameFetchUser(repoFullName);
            if (tracked.isEmpty()) {
                log.debug("Repo {} non tracciato attivamente da alcun utente: scartato.", repoFullName);
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

            // Split per ruolo: il riassunto IA (e il download del diff, costoso) si calcola SOLO se
            // almeno un destinatario è abilitato. Ai ROLE_REGISTERED va il commit grezzo, senza LLM.
            boolean anySummaryUser = tracked.stream()
                    .anyMatch(t -> t.getUser().getRole().canAccessSummaries());

            PushSummary aiSummary = null;
            if (anySummaryUser) {
                // Scarico il diff effettivo del push da GitHub: il payload del webhook non lo contiene.
                String diff = fetchDiff(payload, repoFullName);
                aiSummary = summarizationService.summarizePush(
                        repoFullName, payload.branch(), diff, messages);
                log.info("summary IA title='{}' descLen={}", aiSummary.title(), aiSummary.description().length());
            }
            // Titolo+descrizione del commit di testa, per i ROLE_REGISTERED (calcolato una volta sola).
            PushSummary rawSummary = rawCommitSummary(payload);

            String commitId = payload.headCommitId();
            for (TrackedRepository repo : tracked) {
                User user = repo.getUser();
                PushSummary summary = user.getRole().canAccessSummaries() ? aiSummary : rawSummary;
                if (commitId != null) {
                    notificationService.record(user, repoFullName, summary.title(), summary.description(), commitId);
                }
                // Multi-dispositivo: la notifica va a tutti i device registrati dall'utente.
                for (String token : deviceTokenService.tokensFor(user.getId())) {
                    if (fcmService.sendCommitSummary(token, repoFullName, summary.title(), summary.description())
                            == FcmService.SendOutcome.INVALID_TOKEN) {
                        deviceTokenService.remove(token);
                    }
                }
            }
        } catch (Exception e) {
            // Non rilanciamo: l'errore non deve mai propagarsi al webhook.
            log.error("Errore nell'elaborazione del push: {}", e.getMessage(), e);
        }
    }

    /**
     * Scarica il diff del push da GitHub via installation token. Ritorna stringa vuota (mai null,
     * mai eccezione) se qualcosa non va: in quel caso il riassunto fa fallback sui messaggi di commit.
     *
     * <p>Usa la Compare API ({@code before...after}) per coprire tutti i commit del push; se non c'è
     * un "before" valido (primo push su un branch nuovo) ripiega sul diff del solo commit di testa.
     */
    private String fetchDiff(PushEventPayload payload, String repoFullName) {
        Long installationId = payload.installationId();
        if (installationId == null) {
            log.debug("Push su {} senza installation id: niente diff, uso il fallback.", repoFullName);
            return "";
        }
        int slash = repoFullName.indexOf('/');
        if (slash <= 0 || slash == repoFullName.length() - 1) {
            return "";
        }
        String owner = repoFullName.substring(0, slash);
        String repo = repoFullName.substring(slash + 1);
        try {
            String bearer = "Bearer " + gitHubAppService.getInstallationToken(installationId);
            String before = payload.comparableBefore();
            String head = payload.headCommitId();
            String diff;
            if (before != null && head != null) {
                diff = gitHubApiClient.compareDiff(owner, repo, before + "..." + head, bearer);
            } else if (head != null) {
                diff = gitHubApiClient.commitDiff(owner, repo, head, bearer);
            } else {
                return "";
            }
            if (diff == null) {
                return "";
            }
            return diff.length() > MAX_DIFF_CHARS ? diff.substring(0, MAX_DIFF_CHARS) : diff;
        } catch (Exception e) {
            log.error("Impossibile scaricare il diff di {}: {}", repoFullName, e.getMessage());
            return "";
        }
    }

    /**
     * Notifica "grezza" per gli utenti senza accesso al riassunto IA: nessun LLM, solo il messaggio
     * del commit di testa diviso secondo la convenzione git (prima riga = titolo, resto = descrizione).
     * Ritorna sempre un PushSummary valido (mai null), con il troncamento applicato da PushSummary.
     */
    private PushSummary rawCommitSummary(PushEventPayload payload) {
        String message = headCommitMessage(payload);
        if (message == null || message.isBlank()) {
            return new PushSummary("Nuovo commit", "");
        }
        int newline = message.indexOf('\n');
        if (newline < 0) {
            return new PushSummary(message, "");
        }
        return new PushSummary(message.substring(0, newline), message.substring(newline + 1));
    }

    /** Messaggio del commit di testa (match per id su {@code after}); fallback sull'ultimo commit. */
    private String headCommitMessage(PushEventPayload payload) {
        if (payload.commits() == null || payload.commits().isEmpty()) {
            return null;
        }
        String head = payload.headCommitId();
        if (head != null) {
            for (PushEventPayload.Commit commit : payload.commits()) {
                if (head.equals(commit.id())) {
                    return commit.message();
                }
            }
        }
        return payload.commits().get(payload.commits().size() - 1).message();
    }
}
