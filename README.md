# ContentGrid common

This library provides common conventions for ContentGrid Spring Boot applications.

## Usage

This library is published for Spring Boot 3 and 4. When depending on this library, you need to select the correct
variant.

```groovy
dependencies {
    implementation('com.contentgrid.common:contentgrid-common-spring-boot-starter') {
        capabilities {
            requireFeature('spring-boot-4')
        }
    }
}
```

## Conventions

### Structured logging

The `contentgrid-common-logging` module provides stdout-only newline-delimited JSON logging for Spring Boot 4 services
that use Logback. It is not part of the existing starter; adopt it explicitly:

```groovy
dependencies {
    implementation 'com.contentgrid.common:contentgrid-common-logging:0.1.6-SNAPSHOT'
}
```

The module registers a low-priority `EnvironmentPostProcessor` that supplies these defaults before logging is
initialized:

- `logging.structured.format.console=com.contentgrid.common.logging.ContentGridStructuredLogFormatter`
- `logging.config=classpath:com/contentgrid/common/logging/logback-spring.xml`
- `spring.main.banner-mode=off`

The packaged Logback configuration writes structured records to stdout only. Properties such as `logging.file.name` do
not add a file appender. Explicit application properties override the defaults, and an existing consumer
`logback-test.xml`, `logback.xml` or `logback-spring.xml` is left in place instead of being replaced by the packaged
configuration. When Logback is absent, no Logback-specific defaults are installed.

The `component` field is taken from `spring.application.name`; it is omitted when that property is absent. Application
records contain `timestamp` (numeric Unix seconds with millisecond precision), `type`, `loggerName`, `severity`, `thread`,
`shortMessage`, `fullMessage`, and nested MDC/key-value `context` fields. Source location and caller data are never
collected. `fullMessage` includes the complete throwable, cause and suppressed chain.

The existing `contentgrid-common-spring-boot-starter` is unaffected and does not include this module.

### Actuator security

Allows some actuator endpoints through spring-security.

1. All endpoints are accessible when accessed from the loopback interface
2. Publicly exposed endpoints (`/actuator/health` & `/actuator/info`) are always made accessible
3. Privately exposed endpoints (`/actuator/metrics` & `/actuator/prometheus`) are made accessible when the management
   server runs on a separate port

### Multi-tenant OAuth2 resource server

Allow configuring multiple trusted JWT issuers that will all be trusted.

The configuration property `contentgrid.security.oauth2.trusted-jwt-issuers` can be set to a list of JWT issuer URIs.

The configuration automatically backs-away when a `SecurityFilterChain` is configured.

This is a multi-value alternative to `spring.security.oauth2.resourceserver.jwt.issuer-uri`.

### Disconnected client handling

The default of spring is to accidentally send a HTTP 200 OK response when a connection reset occurs,
even when the connection reset is due to a dependency that is called by the server (not only when the client resets the
connection).

We patch this to return a proper HTTP 500 instead when we are able to.

### Flyway

When using flyway with PostgreSQL, transactional locking is disabled.

This is to be compatible with `CREATE INDEX CONCURRENTLY` and other non-transactional statements,
that don't work together with transactional locking.