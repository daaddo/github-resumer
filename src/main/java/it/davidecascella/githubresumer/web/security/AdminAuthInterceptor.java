package it.davidecascella.githubresumer.web.security;

import it.davidecascella.githubresumer.domain.Role;
import it.davidecascella.githubresumer.domain.User;
import it.davidecascella.githubresumer.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Protegge gli endpoint /api/admin/**: consente l'accesso solo agli utenti con ruolo ROLE_ADMIN.
 * Gira dopo {@link JwtAuthInterceptor}, quindi l'id utente autenticato è già negli attributi.
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    public AdminAuthInterceptor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Object userIdAttr = request.getAttribute(JwtAuthInterceptor.USER_ID_ATTRIBUTE);
        if (!(userIdAttr instanceof Long userId)) {
            // Difensivo: senza autenticazione a monte non possiamo valutare il ruolo.
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        boolean isAdmin = userRepository.findById(userId)
                .map(User::getRole)
                .filter(Role.ROLE_ADMIN::equals)
                .isPresent();

        if (!isAdmin) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;
    }
}
