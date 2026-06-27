package it.davidecascella.githubresumer.web.security;

import it.davidecascella.githubresumer.domain.Role;
import it.davidecascella.githubresumer.domain.User;
import it.davidecascella.githubresumer.repository.UserRepository;
import it.davidecascella.githubresumer.service.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Protegge le PAGINE del pannello admin (/admin/**, escluse login/oauth/logout).
 * A differenza della variante REST ({@link AdminAuthInterceptor}, bearer su /api/admin/**),
 * qui l'autenticazione è via cookie di sessione HttpOnly: se manca o l'utente non è ROLE_ADMIN,
 * si rimanda alla pagina di login invece di restituire un errore JSON.
 */
@Component
public class AdminWebAuthInterceptor implements HandlerInterceptor {

    /** Attributo di richiesta con l'id dell'admin autenticato, per i controller del pannello. */
    public static final String ADMIN_ID_ATTRIBUTE = "adminUserId";
    /** Cookie di sessione del pannello (stesso nome usato da AdminWebController). */
    public static final String SESSION_COOKIE = "gh_admin_session";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AdminWebAuthInterceptor(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = readCookie(request, SESSION_COOKIE);
        if (token != null) {
            try {
                Long userId = jwtService.parseUserId(token);
                boolean isAdmin = userRepository.findById(userId)
                        .map(User::getRole)
                        .filter(Role.ROLE_ADMIN::equals)
                        .isPresent();
                if (isAdmin) {
                    request.setAttribute(ADMIN_ID_ATTRIBUTE, userId);
                    return true;
                }
            } catch (Exception ignored) {
                // token assente/scaduto/non valido → trattato come non autenticato
            }
        }
        redirectToLogin(request, response);
        return false;
    }

    private void redirectToLogin(HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", request.getContextPath() + "/admin/login");
    }

    private static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
