package com.contentgrid.common.spring.autoconfigure.security;

import com.contentgrid.common.spring.actuators.ExposedActuatorEndpoint;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.function.Predicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Conditionally allows requests to actuator endpoints through spring-security.
 * <p>
 * Publicly exposed endpoints {@link ExposedActuatorEndpoint#isAllowPublicExposure()} are always allowed.
 * Other {@link ExposedActuatorEndpoint}-configured endpoints are allowed only when the management server runs on a separate port
 * All endpoints are accessible when connecting from localhost
 */
@AutoConfiguration(
        after = SecurityAutoConfiguration.class
)
@ConditionalOnClass({
        SecurityAutoConfiguration.class, // spring-boot-autoconfigure
        EndpointRequest.class, // spring-boot-actuator-autoconfigure
        SecurityFilterChain.class, // spring-security-web
        HttpServletRequest.class, // jakarta.servlet-api
        HttpSecurity.class // spring-security-config
})
@ConditionalOnWebApplication(type = Type.SERVLET)
public class ContentgridCommonActuatorEndpointsWebSecurityAutoConfiguration {
    @Bean
    @ConditionalOnClass(InfoEndpoint.class)
    ExposedActuatorEndpoint exposedInfoActuatorEndpoint() {
        return new ExposedActuatorEndpoint(InfoEndpoint.class).withAllowPublicExposure(true);
    }

    @Bean
    @ConditionalOnClass(HealthEndpoint.class)
    ExposedActuatorEndpoint exposedHealthActuatorEndpoint() {
        return new ExposedActuatorEndpoint(HealthEndpoint.class).withAllowPublicExposure(true);
    }

    @Bean
    @ConditionalOnClass(PrometheusScrapeEndpoint.class)
    ExposedActuatorEndpoint exposedPrometheusScrapeActuatorEndpoint() {
        return new ExposedActuatorEndpoint(PrometheusScrapeEndpoint.class);
    }

    @Bean
    @ConditionalOnClass(MetricsEndpoint.class)
    ExposedActuatorEndpoint exposedMetricsActuatorEndpoint() {
        return new ExposedActuatorEndpoint(MetricsEndpoint.class);
    }


    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain contentgridCommonActuatorEndpointsSecurityFilterChain(HttpSecurity http,
            Environment environment,
            ObjectProvider<ExposedActuatorEndpoint> exposedEndpoints)
            throws Exception {

        http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests((requests) -> requests.requestMatchers(
                                // Public endpoints are always accessible
                                matcher(exposedEndpoints, ExposedActuatorEndpoint::isAllowPublicExposure),
                                // When connection comes from loopback, all endpoints are accessible
                                request -> isLoopbackAddress(request.getRemoteAddr()),
                                // Exposed endpoints are only accessible when the management server is running on separate port from the main server (for isolation)
                                new AndRequestMatcher(
                                        request -> ManagementPortType.get(environment) == ManagementPortType.DIFFERENT,
                                        matcher(exposedEndpoints,
                                                Predicate.not(ExposedActuatorEndpoint::isAllowPublicExposure))
                                )
                        )
                        .permitAll());

        // all the other /actuator endpoints fall through
        return http.build();
    }

    private static RequestMatcher matcher(ObjectProvider<ExposedActuatorEndpoint> exposedEndpoints,
            Predicate<ExposedActuatorEndpoint> filter) {
        var endpoints = exposedEndpoints.stream().filter(filter).map(ExposedActuatorEndpoint::getEndpoint)
                .toArray(Class[]::new);
        if (endpoints.length == 0) {
            return req -> false;
        }
        return EndpointRequest.to(endpoints);
    }

    private static boolean isLoopbackAddress(String address) {
        try {
            var remoteAddress = InetAddress.getByName(address);
            return remoteAddress.isLoopbackAddress();
        } catch (UnknownHostException ex) {
            return false;
        }
    }
}
