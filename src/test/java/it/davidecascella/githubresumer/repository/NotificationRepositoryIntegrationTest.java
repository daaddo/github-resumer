package it.davidecascella.githubresumer.repository;

import it.davidecascella.githubresumer.AbstractIntegrationTest;
import it.davidecascella.githubresumer.domain.Notification;
import it.davidecascella.githubresumer.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Storage delle notifiche: ordinamento cronologico, paginazione, ricerca per repository e
 * identità applicativa a quattro campi (utente, repository, titolo, commit).
 */
class NotificationRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final String REPO = "torvalds/linux";

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void cleanUp() {
        notificationRepository.deleteAll();
        userRepository.deleteAll();
        user = userRepository.save(User.builder()
                .githubUsername("torvalds")
                .githubAccountId(1024L)
                .build());
    }

    @Test
    void list_returnsMostRecentFirst_andPaginates() {
        // created_at è impostato dall'auditing al save: salvo in ordine, il più vecchio per primo.
        Notification first = notificationRepository.save(notification("Primo commit", "sha1"));
        Notification second = notificationRepository.save(notification("Secondo commit", "sha2"));
        Notification third = notificationRepository.save(notification("Terzo commit", "sha3"));

        Page<Notification> page0 = notificationRepository
                .findByUserIdOrderByCreatedAtDescIdDesc(user.getId(), PageRequest.of(0, 2));

        assertThat(page0.getTotalElements()).isEqualTo(3);
        assertThat(page0.getTotalPages()).isEqualTo(2);
        assertThat(page0.hasNext()).isTrue();
        assertThat(page0.getContent())
                .extracting(Notification::getCommitId)
                .containsExactly(third.getCommitId(), second.getCommitId());

        Page<Notification> page1 = notificationRepository
                .findByUserIdOrderByCreatedAtDescIdDesc(user.getId(), PageRequest.of(1, 2));

        assertThat(page1.hasNext()).isFalse();
        assertThat(page1.getContent())
                .extracting(Notification::getCommitId)
                .containsExactly(first.getCommitId());
    }

    @Test
    void existsBy_identity_isTrueOnlyForSameFourFields() {
        notificationRepository.save(notification("Riassunto", "sha-abc"));

        assertThat(notificationRepository
                .existsByUserIdAndRepoFullNameAndTitleAndCommitId(user.getId(), REPO, "Riassunto", "sha-abc"))
                .isTrue();
        // Cambiare anche solo il commit => notifica diversa.
        assertThat(notificationRepository
                .existsByUserIdAndRepoFullNameAndTitleAndCommitId(user.getId(), REPO, "Riassunto", "sha-xyz"))
                .isFalse();
    }

    @Test
    void search_byRepository_returnsOnlyThatRepo_mostRecentFirst() {
        String otherRepo = "spring-projects/spring-boot";
        notificationRepository.save(notification(REPO, "Linux 1", "lx1"));
        notificationRepository.save(notification(otherRepo, "Boot 1", "bt1"));
        Notification linux2 = notificationRepository.save(notification(REPO, "Linux 2", "lx2"));

        Page<Notification> result = notificationRepository
                .findByUserIdAndRepoFullNameOrderByCreatedAtDescIdDesc(user.getId(), REPO, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .allMatch(n -> n.getRepoFullName().equals(REPO));
        // Più recente per primo.
        assertThat(result.getContent().get(0).getCommitId()).isEqualTo(linux2.getCommitId());
    }

    @Test
    void delete_onlyRemovesNotificationOwnedByUser() {
        User other = userRepository.save(User.builder()
                .githubUsername("linus")
                .githubAccountId(2048L)
                .build());
        Notification mine = notificationRepository.save(notification("Mia", "sha-mine"));
        Notification theirs = notificationRepository.save(Notification.builder()
                .user(other).repoFullName(REPO).title("Loro").commitId("sha-theirs").build());

        // L'utente "other" non possiede la notifica di "user": nessuna riga cancellata.
        assertThat(notificationRepository.deleteByIdAndUserId(mine.getId(), other.getId())).isZero();
        assertThat(notificationRepository.existsById(mine.getId())).isTrue();

        // Il proprietario la cancella: una riga rimossa, quella altrui resta intatta.
        assertThat(notificationRepository.deleteByIdAndUserId(mine.getId(), user.getId())).isEqualTo(1);
        assertThat(notificationRepository.existsById(mine.getId())).isFalse();
        assertThat(notificationRepository.existsById(theirs.getId())).isTrue();
    }

    private Notification notification(String title, String commitId) {
        return notification(REPO, title, commitId);
    }

    private Notification notification(String repoFullName, String title, String commitId) {
        return Notification.builder()
                .user(user)
                .repoFullName(repoFullName)
                .title(title)
                .commitId(commitId)
                .build();
    }
}
