package com.contentgrid.common.logging.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.reactor.netty.NettyServerCustomizer;
import org.springframework.context.annotation.Bean;
import reactor.netty.ReactorNetty;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.logging.AccessLog;

/**
 * Adds structured access logging to Boot's Reactor Netty web server.
 * <p>
 * Access logging stays off unless the JVM property {@code reactor.netty.http.server.accessLogEnabled} is
 * {@code true}. The customizer is only installed when both Boot's Netty web server support and Reactor Netty are
 * present, and an application can replace it by declaring its own {@value #ACCESS_LOG_SERVER_CUSTOMIZER_BEAN_NAME}
 * bean.
 */
@AutoConfiguration
@ConditionalOnClass({NettyServerCustomizer.class, HttpServer.class})
public class NettyAccessLoggingAutoConfiguration {

    static final String ACCESS_LOG_SERVER_CUSTOMIZER_BEAN_NAME = "accessLogServerCustomizer";

    @Bean(name = ACCESS_LOG_SERVER_CUSTOMIZER_BEAN_NAME)
    @ConditionalOnMissingBean(name = ACCESS_LOG_SERVER_CUSTOMIZER_BEAN_NAME)
    NettyServerCustomizer accessLogServerCustomizer() {
        // The format is constant so request URI braces can never become format placeholders.
        return server -> server.accessLog(accessLogEnabled(),
                args -> AccessLog.create("{}", NettyHttpAccessLog.snapshot(args)));
    }

    private static boolean accessLogEnabled() {
        return Boolean.parseBoolean(System.getProperty(ReactorNetty.ACCESS_LOG_ENABLED, "false"));
    }
}
