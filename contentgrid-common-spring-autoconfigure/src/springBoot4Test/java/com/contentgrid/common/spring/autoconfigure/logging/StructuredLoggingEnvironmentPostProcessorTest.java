package com.contentgrid.common.spring.autoconfigure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.common.logging.ContentGridStructuredLogFormatter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;

class StructuredLoggingEnvironmentPostProcessorTest {

    private static final String PACKAGED_LOGGING_CONFIGURATION =
            "classpath:com/contentgrid/common/logging/logback-spring.xml";

    private static final String DEFAULTS_PROPERTY_SOURCE = "contentgrid-logging-defaults";

    @TempDir
    Path tempDir;

    @Test
    void addsStructuredFormatBannerAndPackagedConfigDefaults() {
        var environment = apply(TestClassLoaders.withoutStandardLogbackConfiguration());

        assertThat(environment.getProperty("logging.structured.format.console"))
                .isEqualTo(ContentGridStructuredLogFormatter.class.getName());
        assertThat(environment.getProperty("spring.main.banner-mode")).isEqualTo("off");
        assertThat(environment.getProperty("logging.config")).isEqualTo(PACKAGED_LOGGING_CONFIGURATION);
    }

    @Test
    void addsDefaultsAsLowestPriorityPropertySource() {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("application", Map.of(
                "logging.structured.format.console", "ecs",
                "logging.config", "classpath:custom-logback.xml",
                "spring.main.banner-mode", "console"
        )));

        new StructuredLoggingEnvironmentPostProcessor(TestClassLoaders.withoutStandardLogbackConfiguration())
                .postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
        assertThat(environment.getProperty("logging.config")).isEqualTo("classpath:custom-logback.xml");
        assertThat(environment.getProperty("spring.main.banner-mode")).isEqualTo("console");
        assertThat(environment.getPropertySources().get(DEFAULTS_PROPERTY_SOURCE)).isNotNull();
    }

    @Test
    void doesNotReplaceConsumerLogbackTestXml() throws IOException {
        var consumerConfiguration = consumerConfiguration("logback-test.xml");

        var environment = apply(TestClassLoaders.withStandardLogbackConfiguration(
                "logback-test.xml", consumerConfiguration.toUri().toURL()));

        assertThat(environment.getProperty("logging.config")).isNull();
        assertThat(environment.getProperty("logging.structured.format.console"))
                .isEqualTo(ContentGridStructuredLogFormatter.class.getName());
    }

    @Test
    void doesNotReplaceConsumerLogbackSpringXml() throws IOException {
        var consumerConfiguration = consumerConfiguration("logback-spring.xml");

        var environment = apply(TestClassLoaders.withStandardLogbackConfiguration(
                "logback-spring.xml", consumerConfiguration.toUri().toURL()));

        assertThat(environment.getProperty("logging.config")).isNull();
        assertThat(environment.getProperty("logging.structured.format.console"))
                .isEqualTo(ContentGridStructuredLogFormatter.class.getName());
    }

    @Test
    void doesNotInstallLogbackDefaultsWhenLogbackIsAbsent() {
        var environment = apply(TestClassLoaders.withoutLogback());

        assertThat(environment.getProperty("logging.structured.format.console")).isNull();
        assertThat(environment.getProperty("logging.config")).isNull();
        assertThat(environment.getProperty("spring.main.banner-mode")).isEqualTo("off");
    }

    @Test
    void installsNoDefaultsWhenTheLoggingCoreIsAbsent() {
        var environment = apply(TestClassLoaders.withoutLoggingCore());

        assertThat(environment.getProperty("spring.main.banner-mode")).isNull();
        assertThat(environment.getProperty("logging.structured.format.console")).isNull();
        assertThat(environment.getProperty("logging.config")).isNull();
        assertThat(environment.getPropertySources().get(DEFAULTS_PROPERTY_SOURCE)).isNull();
    }

    @Test
    void isRegisteredThroughTheSpringEnvironmentPostProcessorSpi() {
        var postProcessors = SpringFactoriesLoader.forDefaultResourceLocation(getClass().getClassLoader())
                .load(EnvironmentPostProcessor.class, SpringFactoriesLoader.FailureHandler.handleMessage((message, failure) -> {
                }));

        assertThat(postProcessors).anyMatch(StructuredLoggingEnvironmentPostProcessor.class::isInstance);
    }

    private StandardEnvironment apply(ClassLoader classLoader) {
        var environment = new StandardEnvironment();
        new StructuredLoggingEnvironmentPostProcessor(classLoader)
                .postProcessEnvironment(environment, new SpringApplication());
        return environment;
    }

    private Path consumerConfiguration(String name) throws IOException {
        return Files.writeString(this.tempDir.resolve(name), "<configuration/>");
    }
}
