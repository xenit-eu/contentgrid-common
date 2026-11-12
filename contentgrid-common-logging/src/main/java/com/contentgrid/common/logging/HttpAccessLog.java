package com.contentgrid.common.logging;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Immutable snapshot of a completed HTTP request for access logging.
 * <p>
 * The record only uses Java types so it can be loaded without Reactor Netty.
 */
public record HttpAccessLog(
        BigDecimal timestamp,
        BigDecimal requestTime,
        int responseStatus,
        String requestMethod,
        String requestUri,
        @Nullable String request,
        @Nullable String remoteAddr
) {
}
