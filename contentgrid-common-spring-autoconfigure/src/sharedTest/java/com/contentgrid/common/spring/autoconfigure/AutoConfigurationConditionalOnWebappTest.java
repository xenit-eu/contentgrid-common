package com.contentgrid.common.spring.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"noWebApp"})
public class AutoConfigurationConditionalOnWebappTest {
    @Test
    void contextLoads() {}
}
