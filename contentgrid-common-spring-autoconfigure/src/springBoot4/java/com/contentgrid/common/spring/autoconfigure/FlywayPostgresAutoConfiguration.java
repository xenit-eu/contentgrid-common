package com.contentgrid.common.spring.autoconfigure;

import org.flywaydb.core.Flyway;
import org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureBefore(FlywayAutoConfiguration.class)
@ConditionalOnClass({Flyway.class, PostgreSQLConfigurationExtension.class})
public class FlywayPostgresAutoConfiguration {

    /**
     * The Flyway default of transactional lock enabled conflicts with 'CREATE INDEX CONCURRENTLY' migrations, so it should be disabled by default.
     *
     * @see <a href="https://documentation.red-gate.com/fd/postgresql-transactional-lock-184127530.html">Flyway documentation<a>
     */
    @Bean
    FlywayConfigurationCustomizer postgresqlFlywayConfigurationCustomizerDisableTransactionalLock() {
        return configuration -> {
            configuration.getPluginRegister().getExact(PostgreSQLConfigurationExtension.class).setTransactionalLock(false);
        };
    }
}
