package com.contentgrid.common.spring.actuators;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;
import lombok.With;

/**
 * Marks an actuator endpoint as exposed for spring security
 * <p>
 * Without configuration, endpoints are only accessible from localhost
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExposedActuatorEndpoint {

    /**
     * The endpoint class to expose
     */
    @NonNull
    Class<?> endpoint;

    /**
     * Also make the endpoint accessible when the management server is running on the same port as the main application
     */
    @With
    boolean allowPublicExposure;

    public ExposedActuatorEndpoint(@NonNull Class<?> endpoint) {
        this(endpoint, false);
    }
}
