package com.contentgrid.common.spring.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration(after = WebMvcAutoConfiguration.class)
@ConditionalOnClass({
        WebMvcAutoConfiguration.class, // spring-boot-autoconfigure
        WebMvcConfigurer.class // spring-webmvc
})
public class DisconnectedClientHandlingWebMvcAutoConfiguration {
    @Bean
    DisconnectedClientHandlingWebMvcConfigurer disconnectedClientHandlingWebMvcConfigurer() {
        return new DisconnectedClientHandlingWebMvcConfigurer();
    }

}
