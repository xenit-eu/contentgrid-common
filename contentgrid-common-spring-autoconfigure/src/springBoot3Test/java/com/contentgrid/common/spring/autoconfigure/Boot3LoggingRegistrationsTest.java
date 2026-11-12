package com.contentgrid.common.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.util.ClassUtils;

/**
 * The Spring Boot 3 variant keeps the registrations it always had and carries no logging integration.
 */
class Boot3LoggingRegistrationsTest {

    private static final String CONTENTGRID_PACKAGE = "com.contentgrid.common.";

    private static final String LOGGING_PACKAGE = "com.contentgrid.common.logging.";

    private static final String AUTOCONFIGURE_LOGGING_PACKAGE =
            "com.contentgrid.common.spring.autoconfigure.logging.";

    private static final List<String> EXISTING_AUTO_CONFIGURATIONS = List.of(
            "com.contentgrid.common.spring.autoconfigure.DisconnectedClientHandlingWebMvcAutoConfiguration",
            "com.contentgrid.common.spring.autoconfigure.FlywayPostgresAutoConfiguration",
            "com.contentgrid.common.spring.autoconfigure.security.ContentgridCommonActuatorEndpointsWebSecurityAutoConfiguration",
            "com.contentgrid.common.spring.autoconfigure.security.MultiTenantOAuth2ResourceServerAutoConfiguration"
    );

    private final ClassLoader classLoader = getClass().getClassLoader();

    @Test
    void keepsTheExistingAutoConfigurationImports() {
        List<String> contentgridCandidates = ImportCandidates
                .load(AutoConfiguration.class, this.classLoader)
                .getCandidates()
                .stream()
                .filter(candidate -> candidate.startsWith(CONTENTGRID_PACKAGE))
                .toList();

        assertThat(contentgridCandidates).containsExactlyElementsOf(EXISTING_AUTO_CONFIGURATIONS);
    }

    @Test
    void doesNotContainTheLoggingIntegrationClasses() {
        assertThat(ClassUtils.isPresent(AUTOCONFIGURE_LOGGING_PACKAGE + "StructuredLoggingEnvironmentPostProcessor",
                this.classLoader)).isFalse();
        assertThat(ClassUtils.isPresent(AUTOCONFIGURE_LOGGING_PACKAGE + "NettyAccessLoggingAutoConfiguration",
                this.classLoader)).isFalse();
        assertThat(ClassUtils.isPresent(AUTOCONFIGURE_LOGGING_PACKAGE + "NettyHttpAccessLog",
                this.classLoader)).isFalse();
        assertThat(ClassUtils.isPresent(LOGGING_PACKAGE + "ContentGridStructuredLogFormatter",
                this.classLoader)).isFalse();
        assertThat(ClassUtils.isPresent(LOGGING_PACKAGE + "HttpAccessLog", this.classLoader)).isFalse();
    }

    @Test
    void registersNoLoggingEnvironmentPostProcessor() throws IOException {
        for (URL url : Collections.list(this.classLoader.getResources("META-INF/spring.factories"))) {
            Properties factories = new Properties();
            try (InputStream input = url.openStream()) {
                factories.load(input);
            }
            for (String key : factories.stringPropertyNames()) {
                assertThat(factories.getProperty(key)).as(url.toString())
                        .doesNotContain(LOGGING_PACKAGE)
                        .doesNotContain(AUTOCONFIGURE_LOGGING_PACKAGE);
            }
        }
    }
}
