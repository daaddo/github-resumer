package it.davidecascella.githubresumer.web.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configurazione di sicurezza. Tre catene distinte, valutate nell'ordine dichiarato, perché i tre
 * fronti hanno meccanismi diversi:
 *
 * <ol>
 *   <li><b>/api/**</b> — REST stateless, Bearer JWT ({@link JwtAuthenticationFilter}). /api/admin/**
 *       richiede in più ROLE_ADMIN. Errori in JSON-status: 401 se non autenticato, 403 se non admin.</li>
 *   <li><b>/admin/**</b> — pagine del pannello, cookie di sessione ({@link AdminCookieAuthenticationFilter}).
 *       login/oauth/logout pubblici; il resto richiede ROLE_ADMIN. Su accesso negato si reindirizza
 *       alla pagina di login (non un errore JSON).</li>
 *   <li><b>catch-all</b> — /auth/** e /webhooks/** sono pubblici: l'OAuth gestisce da sé i propri
 *       cookie/state e il webhook valida la firma HMAC di GitHub. CSRF disattivato perché i POST qui
 *       arrivano da client non-browser (app mobile, GitHub), non da un form con sessione.</li>
 * </ol>
 *
 * Tutte le catene sono STATELESS: nessuna HttpSession lato server, la sessione vive nel JWT.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                               JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((req, res, e) ->
                                res.setStatus(HttpServletResponse.SC_FORBIDDEN)));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain adminWebSecurityFilterChain(HttpSecurity http,
                                                    AdminCookieAuthenticationFilter adminCookieAuthenticationFilter) throws Exception {
        http
                .securityMatcher("/admin/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login", "/admin/oauth/**", "/admin/logout").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .addFilterBefore(adminCookieAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        // Niente cookie/non autenticato -> alla pagina di login.
                        .authenticationEntryPoint((req, res, e) ->
                                res.sendRedirect(req.getContextPath() + "/admin/login"))
                        // Autenticato ma non admin -> login con messaggio dedicato.
                        .accessDeniedHandler((req, res, e) ->
                                res.sendRedirect(req.getContextPath() + "/admin/login?error=forbidden")));
        return http.build();
    }

    /** Ultima catena (nessun securityMatcher): tutto il resto è pubblico. */
    @Bean
    @Order(3)
    SecurityFilterChain publicSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
