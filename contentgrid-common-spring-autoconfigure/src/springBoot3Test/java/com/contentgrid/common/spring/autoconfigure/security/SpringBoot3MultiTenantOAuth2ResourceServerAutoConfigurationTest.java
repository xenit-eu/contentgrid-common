package com.contentgrid.common.spring.autoconfigure.security;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

public class SpringBoot3MultiTenantOAuth2ResourceServerAutoConfigurationTest extends MultiTenantOAuth2ResourceServerAutoConfigurationTest {

    @Override
    WebApplicationContextRunner createContextRunner() {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        WebMvcAutoConfiguration.class,
                        ServletWebServerFactoryAutoConfiguration.class,
                        DispatcherServletAutoConfiguration.class,
                        SecurityAutoConfiguration.class,
                        OAuth2ResourceServerAutoConfiguration.class,
                        MultiTenantOAuth2ResourceServerAutoConfiguration.class
                ));
    }

    @Override
    Class<?> getAutoconfigurationClass() {
        return MultiTenantOAuth2ResourceServerAutoConfiguration.class;
    }
}
