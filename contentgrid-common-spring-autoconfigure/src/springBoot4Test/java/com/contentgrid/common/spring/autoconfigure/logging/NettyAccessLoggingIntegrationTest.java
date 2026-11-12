package com.contentgrid.common.spring.autoconfigure.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import com.contentgrid.common.logging.ContentGridStructuredLogFormatter;
import com.contentgrid.common.logging.HttpAccessLog;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.ReactorNetty;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRoutes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class NettyAccessLoggingIntegrationTest {

    private static final String ACCESS_LOG_LOGGER = "reactor.netty.http.server.AccessLog";

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(5);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final List<DisposableServer> servers = new ArrayList<>();

    private final List<Appender<ILoggingEvent>> originalAppenders = new ArrayList<>();

    private Logger accessLogger;

    private Level originalLevel;

    private boolean originalAdditive;

    private String originalAccessLogEnabled;

    private CaptureAppender capture;

    @BeforeEach
    void captureAccessLogEvents() {
        this.originalAccessLogEnabled = System.getProperty(ReactorNetty.ACCESS_LOG_ENABLED);
        this.accessLogger = (Logger) LoggerFactory.getLogger(ACCESS_LOG_LOGGER);
        this.originalLevel = this.accessLogger.getLevel();
        this.originalAdditive = this.accessLogger.isAdditive();
        this.accessLogger.iteratorForAppenders().forEachRemaining(this.originalAppenders::add);
        this.capture = new CaptureAppender(formatter());
        this.capture.setContext(this.accessLogger.getLoggerContext());
        this.capture.start();
        this.accessLogger.setLevel(Level.INFO);
        this.accessLogger.setAdditive(false);
        this.accessLogger.addAppender(this.capture);
    }

    @AfterEach
    void restoreAccessLogging() {
        this.servers.forEach(DisposableServer::disposeNow);
        this.servers.clear();
        this.accessLogger.detachAppender(this.capture);
        this.capture.stop();
        this.originalAppenders.forEach(this.accessLogger::addAppender);
        this.accessLogger.setAdditive(this.originalAdditive);
        this.accessLogger.setLevel(this.originalLevel);
        if (this.originalAccessLogEnabled == null) {
            System.clearProperty(ReactorNetty.ACCESS_LOG_ENABLED);
        }
        else {
            System.setProperty(ReactorNetty.ACCESS_LOG_ENABLED, this.originalAccessLogEnabled);
        }
    }

    @Test
    void disabledByDefaultDoesNotLogCompletedRequests() throws Exception {
        System.clearProperty(ReactorNetty.ACCESS_LOG_ENABLED);

        assertNoAccessRecord();
    }

    @Test
    void disabledWhenPropertyIsFalseDoesNotLogCompletedRequests() throws Exception {
        System.setProperty(ReactorNetty.ACCESS_LOG_ENABLED, "false");

        assertNoAccessRecord();
    }

    @Test
    void enabledLogsOneStructuredAccessRecordPerCompletedRequest() throws Exception {
        System.setProperty(ReactorNetty.ACCESS_LOG_ENABLED, "true");
        var server = startServer(routes -> routes.get("/hello",
                (request, response) -> response.sendString(Mono.just("hello"))));

        var response = this.httpClient.send(get(server, "/hello?name=world&x=1"), BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> assertThat(this.capture.records).hasSize(1));
        String record = this.capture.records.get(0);
        JsonNode json = read(record);
        assertThat(json.get("type").asString()).isEqualTo("access");
        assertThat(json.get("component").asString()).isEqualTo("contentgrid-test");
        assertThat(json.get("timestamp").isNumber()).isTrue();
        assertThat(json.get("requestTime").isNumber()).isTrue();
        assertThat(record).containsPattern("\"requestTime\":\\d+\\.\\d{3},");
        assertThat(json.get("responseStatus").isIntegralNumber()).isTrue();
        assertThat(json.get("responseStatus").asInt()).isEqualTo(200);
        assertThat(json.get("requestMethod").asString()).isEqualTo("GET");
        assertThat(json.get("requestUri").asString()).isEqualTo("/hello?name=world&x=1");
        assertThat(json.get("request").asString()).isEqualTo("GET /hello?name=world&x=1 HTTP/1.1");
        assertThat(json.get("remoteAddr").asString()).isEqualTo("127.0.0.1");
        assertThat(json.propertyNames()).containsExactlyInAnyOrder(
                "timestamp", "type", "component", "requestTime", "responseStatus",
                "requestMethod", "requestUri", "request", "remoteAddr");
    }

    @Test
    void logsOneUnwrappedSnapshotPerRequest() throws Exception {
        System.setProperty(ReactorNetty.ACCESS_LOG_ENABLED, "true");
        var server = startServer(routes -> routes.get("/hello",
                (request, response) -> response.sendString(Mono.just("hello"))));

        this.httpClient.send(get(server, "/hello"), BodyHandlers.ofString());

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> assertThat(this.capture.events).hasSize(1));
        Object[] arguments = this.capture.events.get(0).getArgumentArray();
        assertThat(arguments).hasSize(1);
        assertThat(arguments[0]).isInstanceOf(HttpAccessLog.class);
    }

    @Test
    void recordsFinalErrorStatusAndForwardedAddress() throws Exception {
        System.setProperty(ReactorNetty.ACCESS_LOG_ENABLED, "true");
        var server = startServer(true, routes -> routes.get("/unavailable",
                (request, response) -> response.status(HttpResponseStatus.SERVICE_UNAVAILABLE)
                        .sendString(Mono.just("unavailable"))));

        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/unavailable"))
                .header("Forwarded", "for=203.0.113.7")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        var response = this.httpClient.send(request, BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(503);
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> assertThat(this.capture.records).hasSize(1));
        JsonNode json = read(this.capture.records.get(0));
        assertThat(json.get("responseStatus").isIntegralNumber()).isTrue();
        assertThat(json.get("responseStatus").asInt()).isEqualTo(503);
        assertThat(json.get("remoteAddr").asString()).isEqualTo("203.0.113.7");
    }

    @Test
    void doesNotLogDelayedStreamedResponseBeforeFinalWrite() throws Exception {
        System.setProperty(ReactorNetty.ACCESS_LOG_ENABLED, "true");
        Sinks.Many<String> chunks = Sinks.many().unicast().onBackpressureBuffer();
        var subscribed = new CountDownLatch(1);
        var server = startServer(routes -> routes.get("/stream", (request, response) -> response.sendString(
                chunks.asFlux().doOnSubscribe(subscription -> subscribed.countDown()))));

        var responseFuture = this.httpClient.sendAsync(get(server, "/stream"), BodyHandlers.ofInputStream());
        assertThat(subscribed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(chunks.tryEmitNext("first")).isEqualTo(Sinks.EmitResult.OK);

        HttpResponse<InputStream> response = responseFuture.get(5, TimeUnit.SECONDS);
        assertThat(response.statusCode()).isEqualTo(200);
        try (InputStream body = response.body()) {
            assertThat(body.read()).isNotEqualTo(-1);
            assertThat(this.capture.records).isEmpty();

            assertThat(chunks.tryEmitComplete()).isEqualTo(Sinks.EmitResult.OK);
            body.readAllBytes();

            await().atMost(AWAIT_TIMEOUT)
                    .untilAsserted(() -> assertThat(this.capture.records).hasSize(1));
            assertThat(read(this.capture.records.get(0)).get("responseStatus").asInt()).isEqualTo(200);
        }
    }

    @Test
    void keepsBraceContainingUrisOutOfTheLogFormat() throws Exception {
        System.setProperty(ReactorNetty.ACCESS_LOG_ENABLED, "true");
        var server = startServer(routes -> routes.get("/braces/{name}",
                (request, response) -> response.sendString(Mono.just("braces"))));

        String path = "/braces/%7Bname%7D?filter=%7Bvalue%7D";
        var response = this.httpClient.send(get(server, path), BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> assertThat(this.capture.records).hasSize(1));
        String record = this.capture.records.get(0);
        JsonNode json = read(record);
        assertThat(json.get("requestUri").asString()).isEqualTo(path);
        assertThat(json.get("request").asString()).isEqualTo("GET " + path + " HTTP/1.1");
        assertThat(record).doesNotContain("{}");
    }

    private void assertNoAccessRecord() throws Exception {
        var server = startServer(routes -> routes.get("/hello",
                (request, response) -> response.sendString(Mono.just("hello"))));

        var response = this.httpClient.send(get(server, "/hello"), BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        await().during(Duration.ofMillis(200))
                .atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(this.capture.records).isEmpty());
    }

    private DisposableServer startServer(Consumer<HttpServerRoutes> routes) {
        return startServer(false, routes);
    }

    private DisposableServer startServer(boolean forwarded, Consumer<HttpServerRoutes> routes) {
        HttpServer server = HttpServer.create()
                .host("127.0.0.1")
                .port(0);
        if (forwarded) {
            server = server.forwarded(true);
        }
        var customizer = new NettyAccessLoggingAutoConfiguration().accessLogServerCustomizer();
        var bound = customizer.apply(server.route(routes)).bindNow(Duration.ofSeconds(5));
        this.servers.add(bound);
        return bound;
    }

    private HttpRequest get(DisposableServer server, String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
    }

    private JsonNode read(String json) {
        return this.jsonMapper.readTree(json);
    }

    private static ContentGridStructuredLogFormatter formatter() {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource("test", Map.of("spring.application.name", "contentgrid-test")));
        return new ContentGridStructuredLogFormatter(environment);
    }

    private static final class CaptureAppender extends AppenderBase<ILoggingEvent> {

        private final ContentGridStructuredLogFormatter formatter;

        private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();

        private final List<String> records = new CopyOnWriteArrayList<>();

        private CaptureAppender(ContentGridStructuredLogFormatter formatter) {
            this.formatter = formatter;
        }

        @Override
        protected void append(ILoggingEvent event) {
            this.events.add(event);
            this.records.add(this.formatter.format(event));
        }
    }
}
