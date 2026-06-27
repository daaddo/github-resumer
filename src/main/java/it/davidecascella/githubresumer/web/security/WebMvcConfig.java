package it.davidecascella.githubresumer.web.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;
    private final AdminWebAuthInterceptor adminWebAuthInterceptor;

    public WebMvcConfig(JwtAuthInterceptor jwtAuthInterceptor,
                        AdminAuthInterceptor adminAuthInterceptor,
                        AdminWebAuthInterceptor adminWebAuthInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.adminWebAuthInterceptor = adminWebAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Solo /api/** è protetto. /auth/** e /webhooks/** restano pubblici.
        registry.addInterceptor(jwtAuthInterceptor).addPathPatterns("/api/**");
        // /api/admin/** richiede in più il ruolo ROLE_ADMIN. Registrato dopo il JWT,
        // così l'id utente autenticato è già disponibile quando valuta il ruolo.
        registry.addInterceptor(adminAuthInterceptor).addPathPatterns("/api/admin/**");
        // Pagine del pannello admin (cookie di sessione). Login/oauth/logout restano pubblici.
        registry.addInterceptor(adminWebAuthInterceptor)
                .addPathPatterns("/admin", "/admin/**")
                .excludePathPatterns("/admin/login", "/admin/oauth/start",
                        "/admin/oauth/callback", "/admin/logout");
    }
}
