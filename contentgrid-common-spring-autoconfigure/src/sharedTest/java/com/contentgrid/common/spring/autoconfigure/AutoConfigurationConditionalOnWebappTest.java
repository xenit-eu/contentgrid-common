package com.contentgrid.common.spring.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="+TestApplication.AUTOCONF_DATABASE
})
@ActiveProfiles({"noWebApp"})
public class AutoConfigurationConditionalOnWebappTest {
    @Test
    void contextLoads() {}
}
