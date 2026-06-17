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
 */
@Slf4j
@Service
public class InstallationService {

    private static final int PAGE_SIZE = 100;

    private final GitHubOAuthService oAuthService;
    private final GitHubAppService gitHubAppService;
    private final GitHubApiClient gitHubApiClient;
    private final InstallationRepository installationRepository;
    private final TrackedRepositoryRepository trackedRepositoryRepository;
    private final UserRepository userRepository;

    public InstallationService(GitHubOAuthService oAuthService,
                               GitHubAppService gitHubAppService,
                               GitHubApiClient gitHubApiClient,
                               InstallationRepository installationRepository,
                               TrackedRepositoryRepository trackedRepositoryRepository,
                               UserRepository userRepository) {
        this.oAuthService = oAuthService;
        this.gitHubAppService = gitHubAppService;
        this.gitHubApiClient = gitHubApiClient;
        this.installationRepository = installationRepository;
        this.trackedRepositoryRepository = trackedRepositoryRepository;
        this.userRepository = userRepository;
    }

    /**
     * Setup post-installazione: identifica l'utente dal code, salva la mappa installation->utente
     * e traccia i repo dell'installazione. Ritorna il numero di repo tracciati.
     */
    @Transactional
    public int linkInstallation(String code, long installationId) {
        AuthenticatedGitHubUser auth = oAuthService.authenticateUserFromCode(code);
        Long userId = auth.user().getId();

        // ANTI-IDOR: elenchiamo con il token UTENTE. Se l'utente non ha accesso a questa
        // installazione, GitHub risponde 404 e qui non leghiamo nulla (niente accesso ai repo altrui).
        Set<String> repos = fetchUserInstallationRepoNames(auth.accessToken(), installationId);

        upsertInstallation(installationId, userId, null, null);
        int count = reconcileWith(installationId, userId, repos);
        log.info("Installazione {} collegata all'utente {}: {} repo tracciati.",
                installationId, userId, count);
        return count;
    }

    /**
     * Riconcilia un'installazione a seguito di un webhook (aggiunta/rimozione repo, cambio
     * selezione). Richiede che l'installazione sia già nota (mappata a un utente dal setup).
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
        // è sicuro perché la mappa installation_id -> utente è stata verificata al setup.
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

    private void upsertInstallation(long installationId, Long userId, String accountLogin, String selection) {
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

    /**
     * Repo dell'installazione via token UTENTE: ritorna i repo solo se l'utente ha accesso a
     * quell'installazione (altrimenti GitHub risponde 404). Usato al setup per verificare la
     * proprietà dell'installation_id ed evitare accessi a installazioni altrui.
     */
    private Set<String> fetchUserInstallationRepoNames(String userAccessToken, long installationId) {
        String bearer = "Bearer " + userAccessToken;
        Set<String> names = new HashSet<>();
        int page = 1;
        while (true) {
            InstallationRepositoriesResponse resp =
                    gitHubApiClient.getUserInstallationRepositories(bearer, installationId, PAGE_SIZE, page++);
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
