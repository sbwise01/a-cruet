package com.bradandmarsha.acruet.user.rest;

import jakarta.ws.rs.ApplicationPath;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * JAX-RS wiring for the user-facing WAR.
 */
@ApplicationPath("/")
public class UserJaxRsApplication extends ResourceConfig {

    public UserJaxRsApplication() {
        packages(
                "com.bradandmarsha.acruet.rest",
                "com.bradandmarsha.acruet.auth",
                "com.bradandmarsha.acruet.user.rest"
        );
    }
}
