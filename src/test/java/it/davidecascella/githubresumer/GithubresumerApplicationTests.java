package it.davidecascella.githubresumer;

import org.junit.jupiter.api.Test;

/**
 * Verifica che l'intero context Spring si avvii contro un MySQL reale (Testcontainers),
 * con Flyway che applica le migrazioni e Hibernate che valida lo schema.
 */
class GithubresumerApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }

}
