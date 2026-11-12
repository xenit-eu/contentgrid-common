package com.contentgrid.common.logging;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.ClassUtils;

/**
 * Supplies logging defaults before logging is initialized.
 * <p>
 * The defaults are added as the lowest-priority property source so explicit application properties win. A consumer
 * Logback configuration resource is never replaced by the packaged configuration.
 */
public class StructuredLoggingEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String DEFAULTS_PROPERTY_SOURCE = "contentgrid-logging-defaults";

    static final String PACKAGED_LOGGING_CONFIGURATION =
            "classpath:com/contentgrid/common/logging/logback-spring.xml";

    private static final String LOGBACK_LOGGER_CLASS = "ch.qos.logback.classic.Logger";

    private static final List<String> STANDARD_LOGGING_CONFIGURATIONS = List.of(
            "logback-test.xml", "logback.xml", "logback-spring.xml");

    private final ClassLoader classLoader;

    public StructuredLoggingEnvironmentPostProcessor() {
        this(defaultClassLoader());
    }

    StructuredLoggingEnvironmentPostProcessor(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("spring.main.banner-mode", "off");
        if (ClassUtils.isPresent(LOGBACK_LOGGER_CLASS, this.classLoader)) {
            defaults.put("logging.structured.format.console", ContentGridStructuredLogFormatter.class.getName());
            if (!hasConsumerLoggingConfiguration()) {
                defaults.put("logging.config", PACKAGED_LOGGING_CONFIGURATION);
            }
        }
        environment.getPropertySources().addLast(new MapPropertySource(DEFAULTS_PROPERTY_SOURCE, defaults));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private boolean hasConsumerLoggingConfiguration() {
        return STANDARD_LOGGING_CONFIGURATIONS.stream()
                .anyMatch(configuration -> this.classLoader.getResource(configuration) != null);
    }

    private static ClassLoader defaultClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader != null ? classLoader : StructuredLoggingEnvironmentPostProcessor.class.getClassLoader();
    }
}
