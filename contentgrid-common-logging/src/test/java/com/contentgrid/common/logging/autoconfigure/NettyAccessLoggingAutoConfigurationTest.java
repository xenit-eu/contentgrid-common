package com.contentgrid.common.logging.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.reactor.netty.NettyServerCustomizer;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.http.server.HttpServer;

class NettyAccessLoggingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(NettyAccessLoggingAutoConfiguration.class));

    @Test
    void isDiscoveredThroughTheAutoConfigurationImportsFile() {
        var candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader());

        assertThat(candidates.getCandidates())
                .contains(NettyAccessLoggingAutoConfiguration.class.getName());
    }

    @Test
    void registersTheAccessLogCustomizerWhenBootNettyAndReactorNettyArePresent() {
        this.contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeanNamesForType(NettyServerCustomizer.class))
                    .containsExactly("accessLogServerCustomizer");
        });
    }

    @Test
    void backsOffWithoutBootNettyServerSupport() {
        this.contextRunner.withClassLoader(new FilteredClassLoader(NettyServerCustomizer.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(NettyServerCustomizer.class);
        });
    }

    @Test
    void backsOffWithoutReactorNettyHttpServer() {
        this.contextRunner.withClassLoader(new FilteredClassLoader(HttpServer.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(NettyServerCustomizer.class);
        });
    }

    @Test
    void backsOffWhenTheApplicationReplacesTheAccessLogServerCustomizer() {
        this.contextRunner.withUserConfiguration(ReplacingAccessLogCustomizerConfiguration.class).run(context -> {
            assertThat(context.getBean(NettyServerCustomizer.class))
                    .isSameAs(ReplacingAccessLogCustomizerConfiguration.REPLACEMENT);
        });
    }

    @Test
    void keepsAccessLoggingWhenTheApplicationAddsAnotherNettyCustomizer() {
        this.contextRunner.withUserConfiguration(OtherNettyCustomizerConfiguration.class).run(context -> {
            assertThat(context.getBeanNamesForType(NettyServerCustomizer.class))
                    .containsExactlyInAnyOrder("accessLogServerCustomizer", "otherServerCustomizer");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class ReplacingAccessLogCustomizerConfiguration {

        static final NettyServerCustomizer REPLACEMENT = server -> server;

        @Bean(name = NettyAccessLoggingAutoConfiguration.ACCESS_LOG_SERVER_CUSTOMIZER_BEAN_NAME)
        NettyServerCustomizer accessLogServerCustomizer() {
            return REPLACEMENT;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OtherNettyCustomizerConfiguration {

        @Bean
        NettyServerCustomizer otherServerCustomizer() {
            return server -> server;
        }
    }
}
