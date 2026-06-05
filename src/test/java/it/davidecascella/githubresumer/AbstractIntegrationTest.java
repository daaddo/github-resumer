package it.davidecascella.githubresumer;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base per i test di integrazione: avvia un MySQL reale in un container.
 *
 * - @ServiceConnection collega automaticamente il datasource Spring al container
 *   (niente spring.datasource.* da impostare a mano): Flyway gira le migrazioni
 *   e Hibernate valida lo schema contro un DB vero.
 * - Forniamo qui anche i segreti minimi necessari ad avviare il context (es. il JWT secret),
 *   così non dipendiamo da application-local.properties (assente in CI).
 *
 * Richiede un daemon Docker attivo sulla macchina/runner.
 */
@SpringBootTest
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> "test-jwt-secret-almeno-32-caratteri-123456");
        // Firebase e Anthropic restano disattivati/vuoti: i bean partono in modalità fallback.
        registry.add("firebase.enabled", () -> "false");
    }
}
