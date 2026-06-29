package it.davidecascella.githubresumer.web.security;

import it.davidecascella.githubresumer.domain.User;
import it.davidecascella.githubresumer.repository.UserRepository;
import it.davidecascella.githubresumer.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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
 * Autenticazione Bearer per /api/**: legge "Authorization: Bearer &lt;jwt&gt;", valida il token e
 * popola il SecurityContext con principal = id utente e authority = ruolo
 * (ROLE_REGISTERED/ROLE_USER/ROLE_ADMIN).
 *
 * <p>Il ruolo viene riletto dal DB a ogni richiesta (lookup per chiave primaria): così una
 * revoca/declassamento ha effetto immediato, senza aspettare la scadenza dell'access token.
 *
 * <p>Token assente o non valido: NON impostiamo l'autenticazione e proseguiamo la catena; sarà
 * l'AuthenticationEntrypoint configurato in {@link SecurityConfig} a rispondere 401.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Long userId = jwtService.parseUserId(header.substring(7));
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            user.getId(), null,
                            List.of(new SimpleGrantedAuthority(user.getRole().name())));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                // Token non valido/scaduto: contesto lasciato vuoto -> 401 dall'entry point.
                logger.debug("JWT Bearer non valido: " + e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
