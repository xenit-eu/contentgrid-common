package com.contentgrid.common.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifies that the postgres transactional locking is disabled,
 * by checking that flyway can execute the configuration scripts
 */
@SpringBootTest({
        "spring.flyway.locations=classpath:flyway-postgres-autoconfiguration-test/migration"
})
public class FlywayPostgresAutoConfigurationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgresContainer() {
            return new PostgreSQLContainer<>("postgres:16");
        }
    }

    @Autowired
    Flyway flyway;

    @Test
    void contextLoads() {
        assertThat(flyway.info().current().getVersion())
                .extracting(MigrationVersion::getMajorAsString)
                .isEqualTo("2");

    }
}
