package it.davidecascella.githubresumer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Abilita:
 * - l'elaborazione asincrona (il webhook delega il lavoro pesante a un thread e risponde subito 200);
 * - l'auditing JPA (popolamento automatico di created_at / updated_at).
 */
@Configuration
@EnableAsync
@EnableJpaAuditing
public class AsyncAndAuditingConfig {
}
