package com.contentgrid.common.spring.autoconfigure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.core.io.support.SpringFactoriesLoader;

/**
 * The Spring Boot 4 variant owns the logging registrations and keeps every pre-existing auto-configuration.
 */
class Boot4LoggingRegistrationsTest {

    private static final String LOGGING_PACKAGE = "com.contentgrid.common.spring.autoconfigure.logging.";

    private static final String ENVIRONMENT_POST_PROCESSOR =
            LOGGING_PACKAGE + "StructuredLoggingEnvironmentPostProcessor";

    private static final String NETTY_AUTO_CONFIGURATION = LOGGING_PACKAGE + "NettyAccessLoggingAutoConfiguration";

    private static final List<String> PRE_EXISTING_AUTO_CONFIGURATIONS = List.of(
            "com.contentgrid.common.spring.autoconfigure.DisconnectedClientHandlingWebMvcAutoConfiguration",
            "com.contentgrid.common.spring.autoconfigure.FlywayPostgresAutoConfiguration",
            "com.contentgrid.common.spring.autoconfigure.security.ContentgridCommonActuatorEndpointsWebSecurityAutoConfiguration",
            "com.contentgrid.common.spring.autoconfigure.security.MultiTenantOAuth2ResourceServerAutoConfiguration"
    );

    private final ClassLoader classLoader = getClass().getClassLoader();

    @Test
    void keepsThePreExistingAutoConfigurationsAndAddsTheNettyIntegrationExactlyOnce() {
        List<String> candidates = ImportCandidates
                .load(AutoConfiguration.class, this.classLoader)
                .getCandidates();

        assertThat(candidates).containsAll(PRE_EXISTING_AUTO_CONFIGURATIONS);
        assertThat(candidates.stream().filter(NETTY_AUTO_CONFIGURATION::equals).toList())
                .containsExactly(NETTY_AUTO_CONFIGURATION);
    }

    @Test
    void registersTheLoggingEnvironmentPostProcessorExactlyOnce() {
        List<EnvironmentPostProcessor> postProcessors = SpringFactoriesLoader
                .forDefaultResourceLocation(this.classLoader)
                .load(EnvironmentPostProcessor.class, SpringFactoriesLoader.FailureHandler.handleMessage(
                        (message, failure) -> {
                        }));

        assertThat(postProcessors.stream()
                .filter(postProcessor -> ENVIRONMENT_POST_PROCESSOR.equals(postProcessor.getClass().getName()))
                .toList()).hasSize(1);
    }
}
