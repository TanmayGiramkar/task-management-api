package com.example.taskapi;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Starts a throwaway PostgreSQL 17 container for integration tests.
 * <p>
 * {@code @ServiceConnection} points the DataSource and Flyway at the container (random port, generated
 * credentials), so the tests never use the local {@code task-postgres} database or the {@code .env} file.
 * Spring caches the test context, so test classes with the same configuration share one container.
 * Flyway builds the schema from V1 on every run.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17");
    }
}
