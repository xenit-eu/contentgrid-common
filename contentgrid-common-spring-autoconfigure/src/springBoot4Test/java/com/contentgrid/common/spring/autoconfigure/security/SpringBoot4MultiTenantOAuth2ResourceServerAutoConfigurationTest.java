package com.contentgrid.common.spring.autoconfigure.security;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;

public class SpringBoot4MultiTenantOAuth2ResourceServerAutoConfigurationTest extends
        MultiTenantOAuth2ResourceServerAutoConfigurationTest {

    @Override
    WebApplicationContextRunner createContextRunner() {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        WebMvcAutoConfiguration.class,
                        TomcatServletWebServerAutoConfiguration.class,
                        DispatcherServletAutoConfiguration.class,
                        SecurityAutoConfiguration.class,
                        ServletWebSecurityAutoConfiguration.class,
                        OAuth2ResourceServerAutoConfiguration.class,
                        MultiTenantOAuth2ResourceServerAutoConfiguration.class
                ));
    }

    @Override
    Class<?> getAutoconfigurationClass() {
        return MultiTenantOAuth2ResourceServerAutoConfiguration.class;
    }
}
