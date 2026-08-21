package com.contentgrid.common.spring.autoconfigure.security;

import static com.contentgrid.common.spring.autoconfigure.TestApplication.AUTOCONF_DATABASE;
import static com.contentgrid.common.spring.autoconfigure.TestApplication.filteringJar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentgrid.common.spring.autoconfigure.TestApplication;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Verifies actuator endpoint security when the management server runs on the same port as the main server.
 * <p>
 * Public endpoints (health, info) are accessible from any address.
 * Non-public endpoints (prometheus, metrics) are only accessible from loopback addresses.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="+ AUTOCONF_DATABASE
})
class ActuatorEndpointSecurityTest {

    @Autowired
    WebApplicationContext webApplicationContext;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void healthEndpointIsAccessibleFromExternalAddress() throws Exception {
        mockMvc.perform(get("/actuator/health").with(remoteAddress("192.168.1.100")))
                .andExpect(status().isOk());
    }

    @Test
    void infoEndpointIsAccessibleFromExternalAddress() throws Exception {
        mockMvc.perform(get("/actuator/info").with(remoteAddress("192.168.1.100")))
                .andExpect(status().isOk());
    }

    @Test
    void prometheusEndpointIsBlockedFromExternalAddress() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").with(remoteAddress("192.168.1.100")))
                .andExpect(status().isForbidden());
    }

    @Test
    void metricsEndpointIsBlockedFromExternalAddress() throws Exception {
        mockMvc.perform(get("/actuator/metrics").with(remoteAddress("192.168.1.100")))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthEndpointIsAccessibleFromLoopback() throws Exception {
        mockMvc.perform(get("/actuator/health").with(remoteAddress("127.0.0.1")))
                .andExpect(status().isOk());
    }

    @Test
    void infoEndpointIsAccessibleFromLoopback() throws Exception {
        mockMvc.perform(get("/actuator/info").with(remoteAddress("127.0.0.1")))
                .andExpect(status().isOk());
    }

    @Test
    void prometheusEndpointIsAccessibleFromLoopback() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").with(remoteAddress("127.0.0.1")))
                .andExpect(status().isOk());
    }

    @Test
    void metricsEndpointIsAccessibleFromLoopback() throws Exception {
        mockMvc.perform(get("/actuator/metrics").with(remoteAddress("127.0.0.1")))
                .andExpect(status().isOk());
    }

    static RequestPostProcessor remoteAddress(String addr) {
        return request -> {
            request.setRemoteAddr(addr);
            return request;
        };
    }

    /**
     * Verifies that the autoconfiguration correctly handles missing optional libraries via
     * {@link org.springframework.boot.autoconfigure.condition.ConditionalOnClass}.
     * <p>
     * Each test filters an entire jar from the classpath (by discovering its contents at runtime)
     * to simulate a consumer who does not include that optional dependency.
     */
    @Nested
    class AutoConfigurationConditionTests {

        private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(ContentgridCommonActuatorEndpointsWebSecurityAutoConfiguration.class));

        /**
         * Provides the minimum Spring Security infrastructure needed to make
         * {@link ContentgridCommonActuatorEndpointsWebSecurityAutoConfiguration} start successfully.
         */
        private WebApplicationContextRunner webContextRunner() {
            return new WebApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(ContentgridCommonActuatorEndpointsWebSecurityAutoConfiguration.class))
                    .withUserConfiguration(MinimalSecurityConfiguration.class);
        }

        @Test
        void configurationIsNotAppliedWithoutSpringSecurityWeb() {
            contextRunner
                    .withClassLoader(filteringJar("spring-security-web"))
                    .run(context -> assertThat(context)
                            .hasNotFailed()
                            .doesNotHaveBean(ContentgridCommonActuatorEndpointsWebSecurityAutoConfiguration.class));
        }

        @Test
        void configurationIsNotAppliedWithoutSpringSecurityConfig() {
            contextRunner
                    .withClassLoader(filteringJar("spring-security-config"))
                    .run(context -> assertThat(context)
                            .hasNotFailed()
                            .doesNotHaveBean(ContentgridCommonActuatorEndpointsWebSecurityAutoConfiguration.class));
        }

        @Test
        void configurationIsNotAppliedWithoutServletApi() {
            // At test runtime, HttpServletRequest is provided by tomcat-embed-core
            // instead of jakarta.servlet-api, so we filter the class directly rather than by jar.
            // The servlet application will not be started. Since there is also no webflux application,
            // the autoConfiguration should not be applied.
            contextRunner
                    .withClassLoader(new FilteredClassLoader(HttpServletRequest.class))
                    .run(context -> assertThat(context)
                            .hasNotFailed()
                            .doesNotHaveBean(ContentgridCommonActuatorEndpointsWebSecurityAutoConfiguration.class));
        }

        // spring-boot-actuator-autoconfigure provides EndpointRequest, which is a class-level condition in Spring Boot 3.
        @Test
        void configurationIsNotAppliedWithoutActuatorAutoconfigure_springBoot3() {
            assumeTrue(TestApplication.isSpringBoot3(),
                    "EndpointRequest class-level condition only applies to Spring Boot 3");
            contextRunner
                    .withClassLoader(filteringJar("spring-boot-actuator-autoconfigure"))
                    .run(context -> assertThat(context)
                            .hasNotFailed()
                            .doesNotHaveBean(ContentgridCommonActuatorEndpointsWebSecurityAutoConfiguration.class));
        }

        // spring-boot-security provides SecurityAutoConfiguration, which is a class-level condition in Spring Boot 4.
        @Test
        void configurationIsNotAppliedWithoutSpringBootSecurity_springBoot4() {
            assumeTrue(TestApplication.isSpringBoot4(),
                    "SecurityAutoConfiguration class-level condition only applies to Spring Boot 4");
            contextRunner
                    .withClassLoader(filteringJar("spring-boot-security"))
                    .run(context -> assertThat(context)
                            .hasNotFailed()
                            .doesNotHaveBean(ContentgridCommonActuatorEndpointsWebSecurityAutoConfiguration.class));
        }

        // spring-boot-actuator provides InfoEndpoint in both SB3 and SB4.
        @Test
        void springBootActuatorAbsent_infoEndpointNotExposed() {
            webContextRunner()
                    .withClassLoader(filteringJar("spring-boot-actuator"))
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).doesNotHaveBean("exposedInfoActuatorEndpoint");
                        assertThat(context).hasSingleBean(SecurityFilterChain.class);
                    });
        }

        // spring-boot-health provides HealthEndpoint in Spring Boot 4 (it is part of spring-boot-actuator in SB3).
        @Test
        void springBootHealthAbsent_healthEndpointNotExposed_springBoot4() {
            assumeTrue(TestApplication.isSpringBoot4(),
                    "spring-boot-health is a separate artifact only in Spring Boot 4");
            webContextRunner()
                    .withClassLoader(filteringJar("spring-boot-health"))
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).doesNotHaveBean("exposedHealthActuatorEndpoint");
                        assertThat(context).hasSingleBean(SecurityFilterChain.class);
                    });
        }

        // spring-boot-micrometer-metrics provides both PrometheusScrapeEndpoint and MetricsEndpoint in Spring Boot 4.
        @Test
        void springBootMicrometerMetricsAbsent_springBoot4() {
            assumeTrue(TestApplication.isSpringBoot4(),
                    "spring-boot-micrometer-metrics is a separate artifact only in Spring Boot 4");
            webContextRunner()
                    .withClassLoader(filteringJar("spring-boot-micrometer-metrics"))
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).doesNotHaveBean("exposedPrometheusScrapeActuatorEndpoint");
                        assertThat(context).doesNotHaveBean("exposedMetricsActuatorEndpoint");
                        assertThat(context).hasSingleBean(SecurityFilterChain.class);
                    });
        }

        @Configuration(proxyBeanMethods = false)
        @EnableWebSecurity
        static class MinimalSecurityConfiguration {

        }
    }
}
