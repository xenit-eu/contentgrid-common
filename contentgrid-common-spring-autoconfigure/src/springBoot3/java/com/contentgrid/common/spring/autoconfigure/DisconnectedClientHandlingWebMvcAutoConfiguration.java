package com.contentgrid.common.spring.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(before = WebMvcAutoConfiguration.class)
public class DisconnectedClientHandlingWebMvcAutoConfiguration {
    @Bean
    DisconnectedClientHandlingWebMvcConfigurer disconnectedClientHandlingWebMvcConfigurer() {
        return new DisconnectedClientHandlingWebMvcConfigurer();
    }

}
