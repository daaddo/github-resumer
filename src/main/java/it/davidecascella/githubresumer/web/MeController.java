package it.davidecascella.githubresumer.web;

import it.davidecascella.githubresumer.service.NotificationService;
import it.davidecascella.githubresumer.service.UserService;
import it.davidecascella.githubresumer.web.dto.FcmTokenRequest;
import it.davidecascella.githubresumer.web.dto.NotificationResponse;
import it.davidecascella.githubresumer.web.dto.OrgWithReposResponse;
import it.davidecascella.githubresumer.web.dto.PageResponse;
import it.davidecascella.githubresumer.web.dto.TrackRepoRequest;
import it.davidecascella.githubresumer.web.dto.TrackedRepoResponse;
import it.davidecascella.githubresumer.web.security.JwtAuthInterceptor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Endpoint autenticati per l'utente corrente (token di sessione richiesto). */
@RestController
@RequestMapping("/api/me")
public class MeController {

    /** Limite massimo di elementi per pagina, per proteggere il DB da richieste abusive. */
    private static final int MAX_PAGE_SIZE = 100;

    private final UserService userService;
    private final NotificationService notificationService;

    public MeController(UserService userService, NotificationService notificationService) {
        this.userService = userService;
        this.notificationService = notificationService;
    }

    /** Registra/aggiorna il token FCM del dispositivo. */
    @PutMapping("/fcm-token")
    public ResponseEntity<Void> updateFcmToken(
            @RequestAttribute(JwtAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @Valid @RequestBody FcmTokenRequest request) {
        userService.updateFcmToken(userId, request.fcmToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tracked-repos")
    public List<TrackedRepoResponse> listTrackedRepos(
            @RequestAttribute(JwtAuthInterceptor.USER_ID_ATTRIBUTE) Long userId) {
        return userService.listTrackedRepos(userId).stream()
                .map(TrackedRepoResponse::from)
                .toList();
    }

    @PostMapping("/tracked-repos")
    public ResponseEntity<TrackedRepoResponse> trackRepo(
            @RequestAttribute(JwtAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @Valid @RequestBody TrackRepoRequest request) {
        TrackedRepoResponse response = TrackedRepoResponse.from(
                userService.trackRepo(userId, request.repoFullName()));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/tracked-repos")
    public ResponseEntity<Void> untrackRepo(
            @RequestAttribute(JwtAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @Valid @RequestBody TrackRepoRequest request) {
        userService.untrackRepo(userId, request.repoFullName());
        return ResponseEntity.noContent().build();
    }

    /** Organizzazioni dell'utente con i rispettivi repository */
    @GetMapping("/github-orgs")
    public List<OrgWithReposResponse> listGithubOrgs(
            @RequestAttribute(JwtAuthInterceptor.USER_ID_ATTRIBUTE) Long userId) {
        return userService.getGithubOrgsWithRepos(userId);
    }

    /**
     * Storico paginato delle notifiche dell'utente, ordinato cronologicamente (più recenti prima).
     * Con {@code repo} (es. "torvalds/linux") filtra le notifiche di quel solo repository.
     * {@code page} è 0-based; {@code size} è limitato a {@value #MAX_PAGE_SIZE}.
     */
    @GetMapping("/notifications")
    public PageResponse<NotificationResponse> listNotifications(
            @RequestAttribute(JwtAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestParam(required = false) String repo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        return PageResponse.from(
                notificationService.list(userId, repo, safePage, safeSize),
                NotificationResponse::from);
    }

    /**
     * Elimina una notifica dell'utente corrente. Cancella solo se la notifica appartiene
     * all'utente autenticato; in caso contrario risponde 404.
     */
    @DeleteMapping("/notifications/{id}")
    public ResponseEntity<Void> deleteNotification(
            @RequestAttribute(JwtAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @PathVariable("id") Long id) {
        notificationService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
