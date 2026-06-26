package it.davidecascella.githubresumer;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base per i test di integrazione: avvia un MySQL reale in un container CONDIVISO da tutta la suite.
 *
 * Pattern "singleton container": il container è static e avviato UNA SOLA VOLTA in un blocco statico,
 * volutamente SENZA @Testcontainers/@Container. Motivo del bug che questo evita: con @Container la
 * lifecycle è per-classe (start in beforeAll, STOP in afterAll), mentre il context di @SpringBootTest
 * viene messo in cache e riusato tra le classi di test. Risultato: dopo la prima classe il container
 * veniva fermato e la classe successiva ne avviava uno nuovo su un'altra porta random, ma il pool
 * Hikari del context in cache puntava ancora a quella vecchia -> "Communications link failure".
 * Avviandolo una volta sola (lo rimuove Ryuk alla fine della JVM) il datasource resta valido per
 * l'intera suite, sia in locale sia in CI.
 *
 * - @ServiceConnection collega automaticamente il datasource Spring al container (niente
 *   spring.datasource.* da impostare a mano): Flyway gira le migrazioni e Hibernate valida lo schema.
 * - Forniamo qui anche i segreti minimi necessari ad avviare il context (JWT secret, chiave AES),
 *   così non dipendiamo da application-local.properties (assente in CI).
 *
 * Richiede un daemon Docker attivo sulla macchina/runner.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    static {
        // Avvio esplicito e unico: niente @Container, così il container non viene mai fermato
        // tra una classe di test e l'altra.
        MYSQL.start();
    }

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> "test-jwt-secret-almeno-32-caratteri-123456");
        // Firebase e Anthropic restano disattivati/vuoti: i bean partono in modalità fallback.
        registry.add("firebase.enabled", () -> "false");
        // Chiave AES-256 fittizia per i test (32 byte in Base64).
        registry.add("app.encryption.secret-key", () -> "5YoT6HURdwnKgV3mMgofsP+F4Uu7SIDtTvm/ZvCNV9k=");
    }
}
