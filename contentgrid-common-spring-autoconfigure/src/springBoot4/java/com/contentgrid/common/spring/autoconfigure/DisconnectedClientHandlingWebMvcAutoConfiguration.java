package com.contentgrid.common.spring.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration(after = WebMvcAutoConfiguration.class)
@ConditionalOnClass(WebMvcConfigurer.class)
public class DisconnectedClientHandlingWebMvcAutoConfiguration {
    @Bean
    DisconnectedClientHandlingWebMvcConfigurer disconnectedClientHandlingWebMvcConfigurer() {
        return new DisconnectedClientHandlingWebMvcConfigurer();
    }

}
