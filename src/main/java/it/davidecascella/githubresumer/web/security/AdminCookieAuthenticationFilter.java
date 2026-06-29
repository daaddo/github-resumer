package it.davidecascella.githubresumer.web.security;

import it.davidecascella.githubresumer.domain.User;
import it.davidecascella.githubresumer.repository.UserRepository;
import it.davidecascella.githubresumer.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Autenticazione a cookie per le PAGINE del pannello (/admin/**). Legge il cookie di sessione
 * HttpOnly {@value #SESSION_COOKIE} (contiene il JWT, emesso da AdminWebController) e popola il
 * SecurityContext con principal = id utente e authority = ruolo.
 *
 * <p>Qui si fa solo autenticazione: il vincolo "deve essere ROLE_ADMIN" è espresso come regola di
 * autorizzazione in {@link SecurityConfig}. In assenza/invalidità del cookie il contesto resta
 * vuoto e la catena prosegue: l'entry point del pannello reindirizza alla pagina di login.
 */
@Component
public class AdminCookieAuthenticationFilter extends OncePerRequestFilter {

    /** Cookie di sessione del pannello (emesso da AdminWebController dopo il login OAuth). */
    public static final String SESSION_COOKIE = "gh_admin_session";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AdminCookieAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String token = readCookie(request, SESSION_COOKIE);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Long userId = jwtService.parseUserId(token);
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            user.getId(), null,
                            List.of(new SimpleGrantedAuthority(user.getRole().name())));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception ignored) {
                // cookie assente/scaduto/non valido -> trattato come non autenticato (redirect al login).
            }
        }
        chain.doFilter(request, response);
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
