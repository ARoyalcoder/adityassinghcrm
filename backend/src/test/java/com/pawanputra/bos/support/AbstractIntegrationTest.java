package com.pawanputra.bos.support;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for {@code *IT} tests: boots the whole application on a random port against a real
 * PostgreSQL with all Flyway migrations applied.
 *
 * <p>Where the database comes from, in order:
 * <ol>
 *   <li><b>Docker available</b> (default, used by CI): a PostgreSQL container shared by all IT
 *       classes of the run.</li>
 *   <li><b>{@code -Dit.db.url=...} given</b> (machines without Docker): that server is used. The
 *       tests create and migrate a brand-new schema named {@code it_<random>} and touch nothing
 *       else; the schema is left behind for inspection, so point this at a disposable database.
 *       Optional: {@code -Dit.db.username}, {@code -Dit.db.password}.</li>
 *   <li><b>Neither</b>: the tests are reported as skipped.</li>
 * </ol>
 *
 * <p>Subclasses that write data should be {@code @Transactional} so each test rolls back.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final String EXTERNAL_URL = System.getProperty("it.db.url");
    private static final String EXTERNAL_SCHEMA =
            "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    private static Boolean dockerAvailable;
    private static PostgreSQLContainer container;

    @BeforeAll
    static void requireDatabase() {
        assumeTrue(EXTERNAL_URL != null || dockerAvailable(),
                "Integration tests need Docker, or -Dit.db.url=jdbc:postgresql://host:port/db");
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        if (EXTERNAL_URL != null) {
            String separator = EXTERNAL_URL.contains("?") ? "&" : "?";
            registry.add("spring.datasource.url", () -> EXTERNAL_URL + separator + "currentSchema=" + EXTERNAL_SCHEMA);
            registry.add("spring.datasource.username", () -> System.getProperty("it.db.username", "postgres"));
            registry.add("spring.datasource.password", () -> System.getProperty("it.db.password", ""));
            registry.add("spring.flyway.default-schema", () -> EXTERNAL_SCHEMA);
            registry.add("spring.flyway.create-schemas", () -> "true");
        } else {
            registry.add("spring.datasource.url", () -> container().getJdbcUrl());
            registry.add("spring.datasource.username", () -> container().getUsername());
            registry.add("spring.datasource.password", () -> container().getPassword());
        }
    }

    private static synchronized boolean dockerAvailable() {
        if (dockerAvailable == null) {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        }
        return dockerAvailable;
    }

    /** Started once and reused by every IT class; Testcontainers removes it when the JVM exits. */
    private static synchronized PostgreSQLContainer container() {
        if (container == null) {
            container = new PostgreSQLContainer("postgres:17-alpine");
            container.start();
        }
        return container;
    }
}
