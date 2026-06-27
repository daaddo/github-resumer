package it.davidecascella.githubresumer.web;

import it.davidecascella.githubresumer.repository.UserRepository;
import it.davidecascella.githubresumer.web.dto.AdminUserResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint del pannello di amministrazione. L'accesso è ristretto al ruolo ROLE_ADMIN
 * dall'{@link it.davidecascella.githubresumer.web.security.AdminAuthInterceptor}
 * (registrato su /api/admin/**), quindi qui non serve ricontrollare il ruolo.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;

    public AdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Elenco di tutti gli utenti registrati. */
    @GetMapping("/users")
    public List<AdminUserResponse> listUsers() {
        return userRepository.findAll().stream()
                .map(AdminUserResponse::from)
                .toList();
    }
}
