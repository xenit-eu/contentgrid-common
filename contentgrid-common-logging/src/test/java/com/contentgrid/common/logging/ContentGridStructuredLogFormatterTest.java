package com.contentgrid.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ContentGridStructuredLogFormatterTest {

    private static final long TIMESTAMP = 1662025029085L;

    private static final String ACCESS_LOG_LOGGER_NAME = "reactor.netty.http.server.AccessLog";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void writesTimestampAsNumericUnixSecondsWithMillisecondResolutionOnOneLine() {
        var json = formatter().format(event(Level.INFO, "test"));

        assertThat(json).startsWith("{").endsWith("}\n");
        assertThat(json.chars().filter(character -> character == '\n').count()).isEqualTo(1);
        assertThat(json).contains("\"timestamp\":1662025029.085");

        JsonNode timestamp = read(json).get("timestamp");
        assertThat(timestamp.isNumber()).isTrue();
        assertThat(timestamp.decimalValue()).isEqualByComparingTo("1662025029.085");
    }

    @Test
    void writesApplicationSchemaFields() {
        LoggingEvent event = event(Level.WARN, "hello {}", "world");

        JsonNode json = read(formatter("contentgrid-gateway").format(event));

        assertThat(json.propertyNames()).containsExactlyInAnyOrder(
                "timestamp", "type", "component", "loggerName", "severity", "thread",
                "shortMessage", "fullMessage"
        );
        assertThat(json.get("type").asString()).isEqualTo("application");
        assertThat(json.get("component").asString()).isEqualTo("contentgrid-gateway");
        assertThat(json.get("loggerName").asString())
                .isEqualTo("com.contentgrid.common.logging.ContentGridStructuredLogFormatterTest");
        assertThat(json.get("severity").asString()).isEqualTo("WARN");
        assertThat(json.get("thread").asString()).isEqualTo("test-thread");
        assertThat(json.get("shortMessage").asString()).isEqualTo("hello world");
        assertThat(json.get("fullMessage").asString()).isEqualTo("hello world");
    }

    @Test
    void writesComponentForAnotherServiceName() {
        JsonNode json = read(formatter("contentgrid-test-service").format(event(Level.INFO, "test")));

        assertThat(json.get("component").asString()).isEqualTo("contentgrid-test-service");
    }

    @Test
    void omitsComponentWhenSpringApplicationNameIsAbsent() {
        JsonNode json = read(formatter().format(event(Level.INFO, "test")));

        assertThat(json.propertyNames()).doesNotContain("component");
    }

    @Test
    void appendsFullExceptionChainToFullMessageOnly() {
        var rootCause = new IllegalArgumentException("root-cause");
        var exception = new IllegalStateException("boom", rootCause);
        exception.addSuppressed(new UnsupportedOperationException("suppressed"));
        LoggingEvent event = event(Level.ERROR, "request failed");
        event.setThrowableProxy(new ThrowableProxy(exception));

        var json = formatter("contentgrid-gateway").format(event);

        assertThat(json.chars().filter(character -> character == '\n').count()).isEqualTo(1);
        JsonNode node = read(json);
        assertThat(node.get("severity").asString()).isEqualTo("ERROR");
        assertThat(node.get("shortMessage").asString()).isEqualTo("request failed");
        assertThat(node.get("fullMessage").asString())
                .startsWith("request failed\n")
                .contains("java.lang.IllegalStateException: boom")
                .contains("Caused by: java.lang.IllegalArgumentException: root-cause")
                .contains("Suppressed: java.lang.UnsupportedOperationException: suppressed")
                .contains("ContentGridStructuredLogFormatterTest");
    }

    @Test
    void omitsSourceFieldsAndDoesNotCollectCallerData() {
        LoggingEvent event = new LoggingEvent() {
            @Override
            public StackTraceElement[] getCallerData() {
                throw new AssertionError("caller data must not be collected");
            }

            @Override
            public boolean hasCallerData() {
                throw new AssertionError("caller data must not be collected");
            }
        };
        prepare(event, Level.INFO, "test");

        JsonNode json = read(formatter("contentgrid-gateway").format(event));

        assertThat(json.propertyNames())
                .doesNotContain("sourceClassName", "sourceLineNumber", "sourceMethodName", "callerData");
    }

    @Test
    void escapesQuotesBackslashesNewlinesControlCharactersAndUnicodeAsValidJson() {
        var message = "He said \"hi\" \\ at C:\\tmp\nnext\tline \u0001 café 😀";

        var json = formatter("contentgrid-gateway").format(event(Level.INFO, message));

        assertThat(json).contains("\\\"").contains("\\\\").contains("\\n").contains("\\t").contains("\\u0001");
        assertThat(json.chars().filter(character -> character == '\n').count()).isEqualTo(1);
        assertThat(read(json).get("shortMessage").asString()).isEqualTo(message);
    }

    @Test
    void writesAccessSnapshotAsOneAccessRecord() {
        var accessLog = new HttpAccessLog(
                new BigDecimal("1662025029.085"),
                new BigDecimal("1.021"),
                503,
                "GET",
                "/hello?name=world",
                "GET /hello?name=world HTTP/1.1",
                "203.0.113.7"
        );

        var json = formatter("contentgrid-gateway").format(accessLogEvent(accessLog));

        assertThat(json).startsWith("{").endsWith("}\n");
        assertThat(json.chars().filter(character -> character == '\n').count()).isEqualTo(1);
        JsonNode node = read(json);
        assertThat(node.propertyNames()).containsExactlyInAnyOrder(
                "timestamp", "type", "component", "requestTime", "responseStatus", "requestMethod",
                "requestUri", "request", "remoteAddr"
        );
        assertThat(node.get("timestamp").isNumber()).isTrue();
        assertThat(node.get("timestamp").decimalValue()).isEqualByComparingTo("1662025029.085");
        assertThat(node.get("type").asString()).isEqualTo("access");
        assertThat(node.get("component").asString()).isEqualTo("contentgrid-gateway");
        assertThat(node.get("requestTime").isNumber()).isTrue();
        assertThat(node.get("requestTime").decimalValue()).isEqualByComparingTo("1.021");
        assertThat(node.get("responseStatus").isIntegralNumber()).isTrue();
        assertThat(node.get("responseStatus").asInt()).isEqualTo(503);
        assertThat(node.get("requestMethod").asString()).isEqualTo("GET");
        assertThat(node.get("requestUri").asString()).isEqualTo("/hello?name=world");
        assertThat(node.get("request").asString()).isEqualTo("GET /hello?name=world HTTP/1.1");
        assertThat(node.get("remoteAddr").asString()).isEqualTo("203.0.113.7");
        assertThat(node.propertyNames()).doesNotContain(
                "loggerName", "severity", "thread", "shortMessage", "fullMessage",
                "sourceClassName", "sourceLineNumber", "sourceMethodName"
        );
    }

    @Test
    void omitsUnavailableAccessFields() {
        var unavailable = new HttpAccessLog(
                new BigDecimal("1662025029.085"), BigDecimal.ZERO, 0, "", "", null, null);

        JsonNode json = read(formatter("contentgrid-gateway").format(accessLogEvent(unavailable)));

        assertThat(json.get("responseStatus").asInt()).isZero();
        assertThat(json.propertyNames()).doesNotContain("request", "remoteAddr");
    }

    @Test
    void keepsPlainMessagesAndUnrelatedArgumentsAsApplicationRecords() {
        LoggingEvent plain = event(Level.INFO, "plain");
        LoggingEvent unrelated = event(Level.INFO, "{}", "not-a-snapshot");
        unrelated.setLoggerName(ACCESS_LOG_LOGGER_NAME);
        var snapshot = new HttpAccessLog(
                new BigDecimal("1662025029.085"), BigDecimal.ZERO, 200, "GET", "/", "GET / HTTP/1.1", null);
        LoggingEvent otherLogger = event(Level.INFO, "{}", snapshot);
        otherLogger.setLoggerName("com.example.AccessLog");

        assertApplicationRecord(plain, "plain");
        assertApplicationRecord(unrelated, "not-a-snapshot");
        assertApplicationRecord(otherLogger, snapshot.toString());
    }

    @Test
    void nestsMdcAndKeyValuePairsWithoutOverwritingSchemaFields() {
        LoggingEvent event = event(Level.INFO, "test");
        event.setMDCPropertyMap(Map.of("traceId", "mdc-trace", "timestamp", "not-a-timestamp"));
        event.addKeyValuePair(new KeyValuePair("traceId", "key-value-trace"));
        event.addKeyValuePair(new KeyValuePair("severity", "not-a-severity"));
        event.addKeyValuePair(new KeyValuePair("custom", 42));

        JsonNode json = read(formatter("contentgrid-gateway").format(event));

        assertThat(json.get("timestamp").isNumber()).isTrue();
        assertThat(json.get("severity").asString()).isEqualTo("INFO");
        assertThat(json.get("type").asString()).isEqualTo("application");
        assertThat(json.get("context").get("traceId").asString()).isEqualTo("mdc-trace");
        assertThat(json.get("context").get("timestamp").asString()).isEqualTo("not-a-timestamp");
        assertThat(json.get("context").get("severity").asString()).isEqualTo("not-a-severity");
        assertThat(json.get("context").get("custom").asInt()).isEqualTo(42);
    }

    private ContentGridStructuredLogFormatter formatter() {
        return formatter(null);
    }

    private ContentGridStructuredLogFormatter formatter(String serviceName) {
        var environment = new StandardEnvironment();
        if (serviceName != null) {
            environment.getPropertySources().addFirst(
                    new MapPropertySource("test", Map.of("spring.application.name", serviceName)));
        }
        return new ContentGridStructuredLogFormatter(environment);
    }

    private LoggingEvent accessLogEvent(HttpAccessLog accessLog) {
        LoggingEvent event = event(Level.INFO, "{}");
        event.setLoggerName(ACCESS_LOG_LOGGER_NAME);
        event.setArgumentArray(new Object[] { accessLog });
        return event;
    }

    private void assertApplicationRecord(LoggingEvent event, String shortMessage) {
        JsonNode json = read(formatter("contentgrid-gateway").format(event));
        assertThat(json.get("type").asString()).isEqualTo("application");
        assertThat(json.get("shortMessage").asString()).isEqualTo(shortMessage);
        assertThat(json.propertyNames()).doesNotContain("requestTime", "responseStatus", "requestUri", "remoteAddr");
    }

    private LoggingEvent event(Level level, String message, Object... arguments) {
        var event = new LoggingEvent();
        prepare(event, level, message, arguments);
        return event;
    }

    private void prepare(LoggingEvent event, Level level, String message, Object... arguments) {
        event.setLoggerContext((LoggerContext) LoggerFactory.getILoggerFactory());
        event.setLoggerName("com.contentgrid.common.logging.ContentGridStructuredLogFormatterTest");
        event.setLevel(level);
        event.setThreadName("test-thread");
        event.setInstant(Instant.ofEpochMilli(TIMESTAMP));
        event.setMessage(message);
        if (arguments.length > 0) {
            event.setArgumentArray(arguments);
        }
    }

    private JsonNode read(String json) {
        return this.jsonMapper.readTree(json);
    }
}
