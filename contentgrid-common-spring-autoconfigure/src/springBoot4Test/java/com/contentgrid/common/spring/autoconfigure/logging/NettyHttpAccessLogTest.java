package com.contentgrid.common.spring.autoconfigure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import reactor.netty.http.server.ConnectionInformation;
import reactor.netty.http.server.logging.AccessLogArgProvider;

class NettyHttpAccessLogTest {

    private static final long TIMESTAMP_MILLIS = 1662025029085L;

    @Test
    void mapsProviderValuesToTheSharedRecord() {
        var provider = provider();
        provider.method = "GET";
        provider.uri = "/hello?name=world&x=1";
        provider.protocol = "HTTP/1.1";
        provider.status = "503";
        provider.duration = 1021;

        var snapshot = NettyHttpAccessLog.snapshot(provider);

        assertThat(snapshot.timestamp()).isEqualByComparingTo("1662025029.085");
        assertThat(snapshot.requestTime()).isEqualByComparingTo("1.021");
        assertThat(snapshot.responseStatus()).isEqualTo(503);
        assertThat(snapshot.requestMethod()).isEqualTo("GET");
        assertThat(snapshot.requestUri()).isEqualTo("/hello?name=world&x=1");
        assertThat(snapshot.request()).isEqualTo("GET /hello?name=world&x=1 HTTP/1.1");
        assertThat(snapshot.remoteAddr()).isEqualTo("203.0.113.7");
    }

    @Test
    void materializesTheValuesBecauseReactorNettyReusesItsArgumentProvider() {
        var provider = provider();
        provider.method = "GET";
        provider.uri = "/first";
        provider.status = "200";

        var snapshot = NettyHttpAccessLog.snapshot(provider);
        provider.uri = "/second";
        provider.status = "500";

        assertThat(snapshot.requestUri()).isEqualTo("/first");
        assertThat(snapshot.responseStatus()).isEqualTo(200);
    }

    @Test
    void fallsBackToTheCurrentTimeWhenNoAccessDateTimeIsAvailable() {
        var provider = provider();
        provider.accessDateTime = null;

        long before = System.currentTimeMillis();
        var snapshot = NettyHttpAccessLog.snapshot(provider);
        long after = System.currentTimeMillis();

        assertThat(snapshot.timestamp().scale()).isEqualTo(3);
        assertThat(snapshot.timestamp().multiply(BigDecimal.valueOf(1000)).longValue())
                .isBetween(before, after);
    }

    @Test
    void keepsTheOriginalQueryUriVerbatim() {
        var provider = provider();
        provider.method = "GET";
        provider.uri = "/hello?name=world&x=1";

        var snapshot = NettyHttpAccessLog.snapshot(provider);

        assertThat(snapshot.requestUri()).isEqualTo("/hello?name=world&x=1");
    }

    @Test
    void usesEmptyTextAndZeroStatusWhenTheProviderHasNoValues() {
        var snapshot = NettyHttpAccessLog.snapshot(provider());

        assertThat(snapshot.requestMethod()).isEmpty();
        assertThat(snapshot.requestUri()).isEmpty();
        assertThat(snapshot.responseStatus()).isZero();
        assertThat(snapshot.requestTime()).isEqualByComparingTo("0.000");
    }

    @Test
    void omitsTheRequestLineWhenMethodOrUriIsMissing() {
        var withoutMethod = provider();
        withoutMethod.uri = "/hello";
        var withoutUri = provider();
        withoutUri.method = "GET";

        assertThat(NettyHttpAccessLog.snapshot(withoutMethod).request()).isNull();
        assertThat(NettyHttpAccessLog.snapshot(withoutUri).request()).isNull();
    }

    @Test
    void omitsTheRequestLineProtocolWhenTheProviderHasNoProtocol() {
        var provider = provider();
        provider.method = "GET";
        provider.uri = "/hello";

        assertThat(NettyHttpAccessLog.snapshot(provider).request()).isEqualTo("GET /hello");
    }

    @Test
    void omitsRemoteAddressWithoutConnectionInformation() {
        var provider = provider();
        provider.connectionInformation = null;

        assertThat(NettyHttpAccessLog.snapshot(provider).remoteAddr()).isNull();
    }

    @Test
    void omitsRemoteAddressForNonInetSocketAddress() {
        var provider = provider();
        provider.connectionInformation = connectionInformation(new SocketAddress() {
            @Override
            public String toString() {
                return "unix:/tmp/backend.sock";
            }
        });

        assertThat(NettyHttpAccessLog.snapshot(provider).remoteAddr()).isNull();
    }

    private static MutableAccessLogArgProvider provider() {
        var provider = new MutableAccessLogArgProvider();
        provider.accessDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(TIMESTAMP_MILLIS), ZoneOffset.UTC);
        provider.connectionInformation = connectionInformation(new InetSocketAddress("203.0.113.7", 54321));
        return provider;
    }

    private static ConnectionInformation connectionInformation(SocketAddress remoteAddress) {
        return new ConnectionInformation() {
            @Override
            public SocketAddress hostAddress() {
                return remoteAddress;
            }

            @Override
            public SocketAddress connectionHostAddress() {
                return remoteAddress;
            }

            @Override
            public SocketAddress remoteAddress() {
                return remoteAddress;
            }

            @Override
            public SocketAddress connectionRemoteAddress() {
                return remoteAddress;
            }

            @Override
            public @Nullable String scheme() {
                return "http";
            }

            @Override
            public @Nullable String connectionScheme() {
                return "http";
            }

            @Override
            public @Nullable String hostName() {
                return "localhost";
            }

            @Override
            public int hostPort() {
                return 8080;
            }
        };
    }

    /**
     * Reactor Netty passes a provider whose values change between requests, so the tests can mutate it too.
     */
    @SuppressWarnings("deprecation")
    private static final class MutableAccessLogArgProvider implements AccessLogArgProvider {

        private @Nullable ZonedDateTime accessDateTime;

        private @Nullable ConnectionInformation connectionInformation;

        private @Nullable CharSequence method;

        private @Nullable CharSequence uri;

        private @Nullable String protocol;

        private @Nullable CharSequence status;

        private long duration;

        @Override
        public String zonedDateTime() {
            return String.valueOf(this.accessDateTime);
        }

        @Override
        public @Nullable ZonedDateTime accessDateTime() {
            return this.accessDateTime;
        }

        @Override
        public @Nullable SocketAddress remoteAddress() {
            return this.connectionInformation != null ? this.connectionInformation.remoteAddress() : null;
        }

        @Override
        public @Nullable ConnectionInformation connectionInformation() {
            return this.connectionInformation;
        }

        @Override
        public @Nullable CharSequence method() {
            return this.method;
        }

        @Override
        public @Nullable CharSequence uri() {
            return this.uri;
        }

        @Override
        public @Nullable String protocol() {
            return this.protocol;
        }

        @Override
        public @Nullable String user() {
            return null;
        }

        @Override
        public @Nullable CharSequence status() {
            return this.status;
        }

        @Override
        public long contentLength() {
            return 0;
        }

        @Override
        public long duration() {
            return this.duration;
        }

        @Override
        public @Nullable CharSequence requestHeader(CharSequence name) {
            return null;
        }

        @Override
        public @Nullable CharSequence responseHeader(CharSequence name) {
            return null;
        }

        @Override
        public Map<CharSequence, Set<io.netty.handler.codec.http.cookie.Cookie>> cookies() {
            return Map.of();
        }
    }
}
