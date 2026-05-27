package com.contentgrid.common.spring.autoconfigure;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
@SpringBootTest
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
}
