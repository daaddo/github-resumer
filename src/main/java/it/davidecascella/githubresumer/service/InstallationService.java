package it.davidecascella.githubresumer.service;

import it.davidecascella.githubresumer.client.GitHubApiClient;
import it.davidecascella.githubresumer.client.dto.GitHubRepo;
import it.davidecascella.githubresumer.client.dto.InstallationRepositoriesResponse;
import it.davidecascella.githubresumer.domain.Installation;
import it.davidecascella.githubresumer.domain.TrackedRepository;
import it.davidecascella.githubresumer.domain.User;
import it.davidecascella.githubresumer.repository.InstallationRepository;
import it.davidecascella.githubresumer.repository.TrackedRepositoryRepository;
import it.davidecascella.githubresumer.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Collega le installazioni della GitHub App agli utenti e mantiene i tracked repo allineati.
 *
 * Strategia: "riconcilia tutto", scoped per installazione. I tracked repo con un dato
 * installation_id rispecchiano esattamente i repo correnti di quella installazione su GitHub:
 * ogni cambiamento (aggiunta, rimozione, passaggio selected<->all) converge allo stesso codice.
 * I repo tracciati manualmente (installation_id NULL) non vengono toccati.
 *
 * <p>Le installazioni nascono SOLO dal webhook "installation/created" (server-to-server, firmato):
 * il callback OAuth non scrive nulla. Vale la regola: si installa solo per utenti GIÀ registrati
 * (login a monte). Un'installazione richiesta da un utente sconosciuto viene rifiutata
 * disinstallando la App.
 */
@Slf4j
@Service
public class InstallationService {

    private static final int PAGE_SIZE = 100;

    private final GitHubAppService gitHubAppService;
    private final GitHubApiClient gitHubApiClient;
    private final InstallationRepository installationRepository;
    private final TrackedRepositoryRepository trackedRepositoryRepository;
    private final UserRepository userRepository;

    public InstallationService(GitHubAppService gitHubAppService,
                               GitHubApiClient gitHubApiClient,
                               InstallationRepository installationRepository,
                               TrackedRepositoryRepository trackedRepositoryRepository,
                               UserRepository userRepository) {
        this.gitHubAppService = gitHubAppService;
        this.gitHubApiClient = gitHubApiClient;
        this.installationRepository = installationRepository;
        this.trackedRepositoryRepository = trackedRepositoryRepository;
        this.userRepository = userRepository;
    }

    /**
     * Webhook "installation/created": crea l'installazione e traccia i repo, ma SOLO se l'attore
     * che la porta è un utente già registrato. L'attore è il {@code requester} (chi ha richiesto su
     * un'org) o, in mancanza (installazione diretta), il {@code sender}. Se non è registrato,
     * l'installazione viene RIFIUTATA disinstallando la App (DELETE /app/installations/{id}).
     */
    @Transactional
    public void onInstallationCreated(long installationId, String repositorySelection,
                                      String accountLogin, Long requesterGithubId, Long senderGithubId) {
        Long actorGithubId = requesterGithubId != null ? requesterGithubId : senderGithubId;
        User user = actorGithubId != null
                ? userRepository.findByGithubAccountId(actorGithubId).orElse(null)
                : null;

        if (user == null) {
            log.warn("Installazione {} portata da utente GitHub {} non registrato: rifiuto (disinstallo la App).",
                    installationId, actorGithubId);
            gitHubAppService.deleteInstallation(installationId);
            return;
        }

        upsertInstallation(installationId, user.getId(), accountLogin, repositorySelection,
                requesterGithubId, senderGithubId);
        // Webhook: nessun token utente disponibile. Usiamo l'installation token (server-to-server).
        Set<String> repos = fetchInstallationRepoNames(installationId);
        int count = reconcileWith(installationId, user.getId(), repos);
        log.info("Installazione {} creata per l'utente {}: {} repo tracciati.",
                installationId, user.getId(), count);
    }

    /**
     * Riconcilia un'installazione a seguito di un webhook (aggiunta/rimozione repo, cambio
     * selezione). Richiede che l'installazione sia già nota (creata al "created").
     */
    @Transactional
    public void reconcileInstallation(long installationId, String repositorySelection, String accountLogin) {
        Installation inst = installationRepository.findByInstallationId(installationId).orElse(null);
        if (inst == null) {
            log.warn("Webhook per installazione {} non collegata ad alcun utente: ignorato.", installationId);
            return;
        }
        if (repositorySelection != null) {
            inst.setRepositorySelection(repositorySelection);
        }
        if (accountLogin != null) {
            inst.setAccountLogin(accountLogin);
        }
        installationRepository.save(inst);
        // Webhook: nessun token utente disponibile. Usiamo l'installation token (server-to-server):
        // è sicuro perché la mappa installation_id -> utente è stata verificata al "created".
        Set<String> repos = fetchInstallationRepoNames(installationId);
        int count = reconcileWith(installationId, inst.getUserId(), repos);
        log.info("Installazione {} riconciliata (utente {}): {} repo tracciati.",
                installationId, inst.getUserId(), count);
    }

    /** Disinstallazione: rimuove i tracked repo dell'installazione e la mappatura. */
    @Transactional
    public void removeInstallation(long installationId) {
        Installation inst = installationRepository.findByInstallationId(installationId).orElse(null);
        List<TrackedRepository> tracked = trackedRepositoryRepository.findByInstallationId(installationId);
        if (!tracked.isEmpty()) {
            trackedRepositoryRepository.deleteAll(tracked);
        }
        if (inst != null) {
            installationRepository.delete(inst);
        }
        log.info("Installazione {} rimossa: {} repo non più tracciati.", installationId, tracked.size());
    }

    private void upsertInstallation(long installationId, Long userId, String accountLogin, String selection,
                                    Long requesterGithubId, Long senderGithubId) {
        Installation inst = installationRepository.findByInstallationId(installationId)
                .orElseGet(Installation::new);
        inst.setInstallationId(installationId);
        inst.setUserId(userId);
        if (accountLogin != null) {
            inst.setAccountLogin(accountLogin);
        }
        if (selection != null) {
            inst.setRepositorySelection(selection);
        }
        inst.setRequesterGithubId(requesterGithubId);
        inst.setSenderGithubId(senderGithubId);
        installationRepository.save(inst);
    }

    /**
     * Allinea i tracked repo con installation_id = installationId allo stato corrente su GitHub:
     * aggiunge i nuovi, rimuove quelli non più presenti. Ritorna il numero di repo correnti.
     */
    private int reconcileWith(long installationId, Long userId, Set<String> current) {
        List<TrackedRepository> existing = trackedRepositoryRepository.findByInstallationId(installationId);
        Set<String> existingNames = new HashSet<>();
        for (TrackedRepository t : existing) {
            existingNames.add(t.getRepoFullName());
            if (!current.contains(t.getRepoFullName())) {
                trackedRepositoryRepository.delete(t); // non più nell'installazione -> rimosso
            }
        }

        User userRef = userRepository.getReferenceById(userId);
        for (String fullName : current) {
            if (existingNames.contains(fullName)) {
                continue;
            }
            // Se l'utente lo tracciava già (es. a mano), "adottalo" nell'installazione
            // invece di crearne un duplicato (vincolo unico user_id + repo_full_name).
            TrackedRepository row = trackedRepositoryRepository
                    .findByUserIdAndRepoFullName(userId, fullName)
                    .orElseGet(() -> TrackedRepository.builder()
                            .user(userRef)
                            .repoFullName(fullName)
                            .build());
            row.setInstallationId(installationId);
            trackedRepositoryRepository.save(row);
        }
        return current.size();
    }

    /** Repo dell'installazione via installation token (server-to-server): usare solo dai webhook. */
    private Set<String> fetchInstallationRepoNames(long installationId) {
        String bearer = "Bearer " + gitHubAppService.getInstallationToken(installationId);
        Set<String> names = new HashSet<>();
        int page = 1;
        while (true) {
            InstallationRepositoriesResponse resp =
                    gitHubApiClient.getInstallationRepositories(bearer, PAGE_SIZE, page++);
            for (GitHubRepo repo : resp.repositories()) {
                names.add(repo.fullName());
            }
            if (resp.repositories().size() < PAGE_SIZE) {
                break;
            }
        }
        return names;
    }
}
