package it.davidecascella.githubresumer.web;

import it.davidecascella.githubresumer.service.UserService;
import it.davidecascella.githubresumer.web.dto.FcmTokenRequest;
import it.davidecascella.githubresumer.web.dto.TrackRepoRequest;
import it.davidecascella.githubresumer.web.dto.TrackedRepoResponse;
import it.davidecascella.githubresumer.web.security.JwtAuthInterceptor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Endpoint autenticati per l'utente corrente (token di sessione richiesto). */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
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
}
