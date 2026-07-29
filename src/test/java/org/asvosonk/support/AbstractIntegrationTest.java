package org.asvosonk.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests running against a real, disposable
 * PostgreSQL 16 instance started by Testcontainers.
 *
 * <p>The container image ({@code postgres:16-alpine}) matches the production
 * database declared in {@code docker-compose.yaml}, so the tests validate
 * against the exact engine used in production. Testcontainers picks an
 * ephemeral port and generates throw-away credentials, so this suite requires
 * <strong>no</strong> pre-running database and holds <strong>no</strong>
 * hard-coded secret.
 *
 * <p>{@link ServiceConnection} lets Spring Boot auto-wire the datasource URL,
 * username and password from the running container, overriding the values in
 * {@code application.properties}. Everything else is inherited: Flyway applies
 * the full V1..V11 migration set on the fresh database and Hibernate runs in
 * {@code validate} mode, so any divergence between the JPA entities and the
 * real schema fails the test at context startup — exactly the safety net the
 * remediation requires.
 *
 * <p>The container is {@code static}, so it is started once and reused across
 * all test methods of a class (and across classes when the Spring context is
 * cached). Each test method should be {@code @Transactional} to roll back its
 * changes automatically.
 *
 * <p>Prerequisite: a reachable Docker daemon during {@code mvn test}.
 */
public abstract class AbstractIntegrationTest {

    /**
     * Singleton container pattern: the container is started once for the whole
     * JVM (not per test class) and never explicitly stopped. Testcontainers'
     * Ryuk sidecar tears it down when the JVM exits.
     *
     * <p>The {@code @Container}/{@code @Testcontainers} JUnit lifecycle would
     * instead stop the container after each class. Because Spring caches the
     * ApplicationContext across test classes that share this configuration, a
     * cached context would keep pointing at the first (already-stopped)
     * container's ephemeral port, causing "connection refused" in later
     * classes. Starting once and leaving it running avoids that mismatch.
     */
    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("asvosonk");

    static {
        POSTGRES.start();
    }
}
