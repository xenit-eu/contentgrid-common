package com.contentgrid.common.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.DisconnectedClientHelper;

/**
 * Verifies that a disconnected client exception (detected via "broken pipe" in the message)
 * results in HTTP 500 rather than a silent 200 OK.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="+TestApplication.AUTOCONF_DATABASE
})
class DisconnectedClientHandlingWebMvcAutoConfigurationTest {

    @Autowired
    WebApplicationContext webApplicationContext;

    MockMvc mockMvc;

    private static final IOException DISCONNECTED_EXCEPTION = new IOException("broken pipe");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void exceptionIsRecognizedAsDisconnectedClient() {
        assertThat(DisconnectedClientHelper.isClientDisconnectedException(DISCONNECTED_EXCEPTION)).isTrue();
    }

    @Test
    @WithMockUser
    void disconnectedClientException_returns500() throws Exception {
        mockMvc.perform(get("/disconnected-client"))
                .andExpect(status().isInternalServerError());
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @RestController
    static class TestController {
        @GetMapping("/disconnected-client")
        void throwDisconnectedClientException() throws IOException {
            throw DISCONNECTED_EXCEPTION;
        }
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
                        AutoConfigurations.of(DisconnectedClientHandlingWebMvcAutoConfiguration.class));

        @Test
        void configurationIsNotAppliedWithoutSpringWebmvc() {
            contextRunner
                    .withClassLoader(TestApplication.filteringJar("spring-webmvc"))
                    .run(context -> assertThat(context)
                            .hasNotFailed()
                            .doesNotHaveBean(DisconnectedClientHandlingWebMvcAutoConfiguration.class));
        }

        // spring-boot-webmvc provides WebMvcAutoConfiguration in Spring Boot 4 (it is part of spring-boot-autoconfigure in SB3).
        @Test
        void configurationIsNotAppliedWithoutSpringBootWebmvc_springBoot4() {
            assumeTrue(TestApplication.isSpringBoot4(),
                    "WebMvcAutoConfiguration from spring-boot-webmvc is a Boot 4 artifact");
            contextRunner
                    .withClassLoader(TestApplication.filteringJar("spring-boot-webmvc"))
                    .run(context -> assertThat(context)
                            .hasNotFailed()
                            .doesNotHaveBean(DisconnectedClientHandlingWebMvcAutoConfiguration.class));
        }

    }
}
