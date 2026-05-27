package com.contentgrid.common.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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
@SpringBootTest
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
}
