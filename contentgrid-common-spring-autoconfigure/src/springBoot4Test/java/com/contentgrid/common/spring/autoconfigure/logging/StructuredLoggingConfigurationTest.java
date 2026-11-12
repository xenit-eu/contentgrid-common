package com.contentgrid.common.spring.autoconfigure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.common.logging.ContentGridStructuredLogFormatter;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class StructuredLoggingConfigurationTest {

    private static final String PACKAGED_LOGGING_CONFIGURATION =
            "classpath:com/contentgrid/common/logging/logback-spring.xml";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @TempDir
    Path tempDir;

    private PrintStream originalOut;

    private PrintStream originalErr;

    private Properties originalSystemProperties;

    private ByteArrayOutputStream stdout;

    private ByteArrayOutputStream stderr;

    private LogbackLoggingSystem loggingSystem;

    @BeforeEach
    void loadProductionLoggingConfiguration() {
        this.originalOut = System.out;
        this.originalErr = System.err;
        this.originalSystemProperties = new Properties();
        this.originalSystemProperties.putAll(System.getProperties());
        this.stdout = new ByteArrayOutputStream();
        this.stderr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(this.stdout, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(this.stderr, true, StandardCharsets.UTF_8));

        this.loggingSystem = new LogbackLoggingSystem(getClass().getClassLoader());
        this.loggingSystem.cleanUp();
        this.loggingSystem.initialize(new LoggingInitializationContext(productionEnvironment()),
                PACKAGED_LOGGING_CONFIGURATION, null);
    }

    @AfterEach
    void restoreTestLoggingConfiguration() {
        try {
            this.loggingSystem.cleanUp();
            System.setOut(this.originalOut);
            System.setErr(this.originalErr);
            this.loggingSystem.initialize(new LoggingInitializationContext(new StandardEnvironment()),
                    "classpath:logback-test.xml", null);
        }
        finally {
            System.setProperties(this.originalSystemProperties);
        }
    }

    @Test
    void packagedLoggingConfigurationIsOnTheClasspath() {
        assertThat(new ClassPathResource("com/contentgrid/common/logging/logback-spring.xml").exists()).isTrue();
    }

    @Test
    void writesOneJsonRecordPerApplicationEventToStdoutAndNothingToStderr() {
        var logger = LoggerFactory.getLogger("com.contentgrid.common.logging.StructuredLoggingConfigurationTest");
        logger.info("hello {}", "world");
        logger.error("request failed", new IllegalStateException("boom"));
        System.out.flush();
        System.err.flush();

        assertThat(this.stderr.toString(StandardCharsets.UTF_8)).isEmpty();
        List<String> records = this.stdout.toString(StandardCharsets.UTF_8).lines().toList();
        assertThat(records).hasSize(2).allSatisfy(record -> assertThat(record).isNotBlank());

        JsonNode info = read(records.get(0));
        assertThat(info.get("type").asString()).isEqualTo("application");
        assertThat(info.get("severity").asString()).isEqualTo("INFO");
        assertThat(info.get("shortMessage").asString()).isEqualTo("hello world");
        assertThat(info.get("fullMessage").asString()).isEqualTo("hello world");

        JsonNode error = read(records.get(1));
        assertThat(error.get("severity").asString()).isEqualTo("ERROR");
        assertThat(error.get("shortMessage").asString()).isEqualTo("request failed");
        assertThat(error.get("fullMessage").asString())
                .contains("java.lang.IllegalStateException: boom")
                .contains("StructuredLoggingConfigurationTest");
    }

    @Test
    void configuresOnlyAStructuredConsoleAppenderEvenWithLoggingFileProperties() {
        LoggerFactory.getLogger("com.contentgrid.common.logging.StructuredLoggingConfigurationTest").info("hello");
        System.out.flush();

        List<Appender<ILoggingEvent>> appenders = allAppenders();
        assertThat(appenders).singleElement().isInstanceOf(ConsoleAppender.class);
        var consoleAppender = (ConsoleAppender<ILoggingEvent>) appenders.get(0);
        assertThat(consoleAppender.getEncoder()).isInstanceOf(StructuredLogEncoder.class);
        assertThat(appenders).noneMatch(appender -> appender instanceof FileAppender);
        assertThat(this.tempDir).isEmptyDirectory();
    }

    @Test
    void environmentDefaultsSelectTheSharedFormatterAndDisableTheBanner() {
        var environment = productionEnvironment();

        assertThat(environment.getProperty("logging.structured.format.console"))
                .isEqualTo(ContentGridStructuredLogFormatter.class.getName());
        assertThat(environment.getProperty("spring.main.banner-mode")).isEqualTo("off");
        assertThat(environment.getProperty("logging.config")).isEqualTo(PACKAGED_LOGGING_CONFIGURATION);
    }

    private List<Appender<ILoggingEvent>> allAppenders() {
        var loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        List<Appender<ILoggingEvent>> appenders = new ArrayList<>();
        for (Logger logger : loggerContext.getLoggerList()) {
            logger.iteratorForAppenders().forEachRemaining(appenders::add);
        }
        return appenders;
    }

    private StandardEnvironment productionEnvironment() {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-file-logging", Map.of(
                "logging.file.name", this.tempDir.resolve("service.log").toString(),
                "logging.file.path", this.tempDir.toString()
        )));
        new StructuredLoggingEnvironmentPostProcessor(TestClassLoaders.withoutStandardLogbackConfiguration())
                .postProcessEnvironment(environment, new SpringApplication());
        return environment;
    }

    private JsonNode read(String json) {
        return this.jsonMapper.readTree(json);
    }
}
