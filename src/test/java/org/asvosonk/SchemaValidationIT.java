package org.asvosonk;

import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that the application context boots against a real PostgreSQL 16 database:
 * Flyway applies every migration (V1..V11) and Hibernate validates the schema
 * ({@code ddl-auto=validate}) against the JPA entities.
 *
 * <p>This is the foundational regression net for the remediation: any
 * entity/schema mismatch (e.g. a missing {@code columnDefinition} on a named
 * enum) will make this test fail at startup rather than in production.
 */
@SpringBootTest
class SchemaValidationIT extends AbstractIntegrationTest {

    @Test
    void contextLoadsAndSchemaValidates() {
        // Success = Flyway migrated cleanly AND Hibernate `validate` found no mismatch.
        // The assertion is the successful context startup itself.
    }
}
