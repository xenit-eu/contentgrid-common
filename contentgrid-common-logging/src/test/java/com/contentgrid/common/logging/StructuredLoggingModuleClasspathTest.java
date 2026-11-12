package com.contentgrid.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.util.ClassUtils;

/**
 * This module ships the shared formatter, the access record and the packaged Logback configuration. Automatic
 * integration is owned by the Spring Boot 4 variant of {@code contentgrid-common-spring-autoconfigure}.
 */
class StructuredLoggingModuleClasspathTest {

    private static final String LOGGING_PACKAGE = "com.contentgrid.common.logging.";

    private static final String AUTOCONFIGURE_LOGGING_PACKAGE =
            "com.contentgrid.common.spring.autoconfigure.logging.";

    private static final String SPRING_FACTORIES = "META-INF/spring.factories";

    private static final String AUTO_CONFIGURATION_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private final ClassLoader classLoader = getClass().getClassLoader();

    @Test
    void keepsTheSharedFormatterAccessRecordAndPackagedConfiguration() {
        assertThat(ClassUtils.isPresent(LOGGING_PACKAGE + "ContentGridStructuredLogFormatter", this.classLoader))
                .isTrue();
        assertThat(ClassUtils.isPresent(LOGGING_PACKAGE + "HttpAccessLog", this.classLoader)).isTrue();
        assertThat(this.classLoader.getResource("com/contentgrid/common/logging/logback-spring.xml")).isNotNull();
    }

    @Test
    void registersNoEnvironmentPostProcessor() throws IOException {
        for (URL url : Collections.list(this.classLoader.getResources(SPRING_FACTORIES))) {
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

    @Test
    void registersNoAutoConfiguration() throws IOException {
        for (URL url : Collections.list(this.classLoader.getResources(AUTO_CONFIGURATION_IMPORTS))) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                assertThat(reader.lines().toList()).as(url.toString())
                        .noneMatch(candidate -> candidate.startsWith(LOGGING_PACKAGE)
                                || candidate.startsWith(AUTOCONFIGURE_LOGGING_PACKAGE));
            }
        }
    }

    @Test
    void doesNotContainTheMovedIntegrationClasses() {
        assertThat(ClassUtils.isPresent(LOGGING_PACKAGE + "StructuredLoggingEnvironmentPostProcessor",
                this.classLoader)).isFalse();
        assertThat(ClassUtils.isPresent(LOGGING_PACKAGE + "autoconfigure.NettyAccessLoggingAutoConfiguration",
                this.classLoader)).isFalse();
        assertThat(ClassUtils.isPresent(LOGGING_PACKAGE + "autoconfigure.NettyHttpAccessLog",
                this.classLoader)).isFalse();
    }
}
