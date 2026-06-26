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
}
