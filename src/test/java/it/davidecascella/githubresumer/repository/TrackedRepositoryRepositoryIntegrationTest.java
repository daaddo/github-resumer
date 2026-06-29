package it.davidecascella.githubresumer.repository;

import it.davidecascella.githubresumer.AbstractIntegrationTest;
import it.davidecascella.githubresumer.domain.TrackedRepository;
import it.davidecascella.githubresumer.domain.User;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Caricamento dello User associato a un TrackedRepository, dal punto di vista del webhook.
 *
 * Contesto della regressione (2026-06-23): il webhook elabora i push in un thread @Async, senza
 * sessione Hibernate aperta (in prod open-in-view=false). L'associazione TrackedRepository -> User
 * e' LAZY, quindi accedere a un campo dello User fuori sessione lancia LazyInitializationException.
 * findByRepoFullNameFetchUser carica lo User nella STESSA query (JOIN FETCH) per evitarlo.
 *
 * Questi test NON sono @Transactional di proposito: cosi' la sessione si chiude tra la query del
 * repository e l'accesso allo User, riproducendo esattamente la condizione del thread @Async.
 */
class TrackedRepositoryRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final String REPO_FULL_NAME = "torvalds/linux";
    private static final String GITHUB_USERNAME = "torvalds";

    @Autowired
    private TrackedRepositoryRepository trackedRepositoryRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanUp() {
        // Ordine: prima i figli (FK su user_id), poi gli utenti.
        trackedRepositoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    /** Il fix: con JOIN FETCH lo User e' gia' materializzato, accessibile anche senza sessione. */
    @Test
    void findByRepoFullNameFetchUser_materializesUser_outsideSession() {
        seedTrackedRepoWithUser();

        // Nessuna transazione attiva qui: simula il thread @Async del webhook.
        List<TrackedRepository> tracked = trackedRepositoryRepository.findByRepoFullNameFetchUser(REPO_FULL_NAME);

        assertThat(tracked).hasSize(1);
        // L'accesso allo User fuori sessione NON deve lanciare e il campo deve essere quello salvato.
        assertThat(tracked.get(0).getUser().getGithubUsername()).isEqualTo(GITHUB_USERNAME);
    }

    /**
     * Guardia anti-regressione: documenta perche' esiste il metodo fetch. Con il finder semplice
     * lo User resta un proxy lazy e accederlo fuori sessione esplode, com'e' successo nel webhook.
     * Se qualcuno rimette findByRepoFullName nel flusso @Async, questo test ricorda il perche' del bug.
     */
    @Test
    void findByRepoFullName_lazyUser_throwsOutsideSession() {
        seedTrackedRepoWithUser();

        List<TrackedRepository> tracked = trackedRepositoryRepository.findByRepoFullName(REPO_FULL_NAME);
        assertThat(tracked).hasSize(1);

        assertThatThrownBy(() -> tracked.get(0).getUser().getGithubUsername())
                .isInstanceOf(LazyInitializationException.class);
    }

    /**
     * Gate del digest: lo stesso repo è seguito da due utenti, ma solo uno ha tracked = true.
     * La query del webhook deve restituire SOLO quella riga (l'altro lo "segue" ma è silenziato)
     * e materializzare lo User fuori sessione.
     */
    @Test
    void findTrackedByRepoFullNameFetchUser_returnsOnlyTrackedRows_andMaterializesUser() {
        seedTrackedRepoForUser(GITHUB_USERNAME, 1024L, true);
        seedTrackedRepoForUser("silent-watcher", 2048L, false);

        List<TrackedRepository> tracked =
                trackedRepositoryRepository.findTrackedByRepoFullNameFetchUser(REPO_FULL_NAME);

        assertThat(tracked).hasSize(1);
        assertThat(tracked.get(0).isTracked()).isTrue();
        // JOIN FETCH: lo User è accessibile anche senza sessione (come nel thread @Async del webhook).
        assertThat(tracked.get(0).getUser().getGithubUsername()).isEqualTo(GITHUB_USERNAME);
    }

    /** Se il repo è seguito ma nessuno lo traccia attivamente, il gate è vuoto: niente digest. */
    @Test
    void findTrackedByRepoFullNameFetchUser_empty_whenFollowedButNobodyTracks() {
        seedTrackedRepoForUser(GITHUB_USERNAME, 1024L, false);

        assertThat(trackedRepositoryRepository.findTrackedByRepoFullNameFetchUser(REPO_FULL_NAME))
                .isEmpty();
    }

    /** Opt-out: una riga creata senza specificare il flag nasce con tracked = true. */
    @Test
    void trackedFlag_defaultsTrue() {
        seedTrackedRepoWithUser();

        TrackedRepository row = trackedRepositoryRepository.findByRepoFullName(REPO_FULL_NAME).get(0);
        assertThat(row.isTracked()).isTrue();
    }

    private void seedTrackedRepoWithUser() {
        User user = userRepository.save(User.builder()
                .githubUsername(GITHUB_USERNAME)
                .githubAccountId(1024L)
                .githubAccessToken("gho_test_token")
                .build());

        trackedRepositoryRepository.save(TrackedRepository.builder()
                .user(user)
                .repoFullName(REPO_FULL_NAME)
                .build());
    }

    private void seedTrackedRepoForUser(String username, long accountId, boolean tracked) {
        User user = userRepository.save(User.builder()
                .githubUsername(username)
                .githubAccountId(accountId)
                .githubAccessToken("gho_test_token")
                .build());

        trackedRepositoryRepository.save(TrackedRepository.builder()
                .user(user)
                .repoFullName(REPO_FULL_NAME)
                .tracked(tracked)
                .build());
    }
}
