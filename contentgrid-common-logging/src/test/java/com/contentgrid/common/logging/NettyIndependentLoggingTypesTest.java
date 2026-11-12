package com.contentgrid.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The generic formatter and access record ship with the module and must stay loadable in services without Netty.
 * <p>
 * The check inspects internal class names in the constant pool, so the {@code reactor.netty.http.server.AccessLog}
 * logger name of the formatter is not confused with a type reference.
 */
class NettyIndependentLoggingTypesTest {

    private static final String[] NETTY_CLASS_NAME_PREFIXES = {
            "reactor/netty/", "io/netty/", "org/springframework/boot/netty/", "org/springframework/boot/reactor/netty/"
    };

    @Test
    void contentGridStructuredLogFormatterReferencesNoNettyTypes() throws IOException {
        assertNoNettyTypeReferences(ContentGridStructuredLogFormatter.class);
    }

    @Test
    void httpAccessLogReferencesNoNettyTypes() throws IOException {
        assertNoNettyTypeReferences(HttpAccessLog.class);
    }

    private void assertNoNettyTypeReferences(Class<?> type) throws IOException {
        String classFile = type.getName().replace('.', '/') + ".class";
        try (InputStream classBytes = type.getClassLoader().getResourceAsStream(classFile)) {
            assertThat(classBytes).as(classFile).isNotNull();
            String constantPool = new String(classBytes.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertThat(constantPool).as(classFile).doesNotContain(NETTY_CLASS_NAME_PREFIXES);
        }
    }
}
