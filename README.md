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

### Actuator security

Allows some actuator endpoints through spring-security.

1. All endpoints are accessible when accessed from the loopback interface
2. Publicly exposed endpoints (`/actuator/health` & `/actuator/info`) are always made accessible
3. Privately exposed endpoints (`/actuator/metrics` & `/actuator/prometheus`) are made accessible when the management
   server runs on a separate port

### Disconnected client handling

The default of spring is to accidentally send a HTTP 200 OK response when a connection reset occurs,
even when the connection reset is due to a dependency that is called by the server (not only when the client resets the
connection).

We patch this to return a proper HTTP 500 instead when we are able to.

### Flyway

When using flyway with PostgreSQL, transactional locking is disabled.

This is to be compatible with `CREATE INDEX CONCURRENTLY` and other non-transactional statements,
that don't work together with transactional locking.