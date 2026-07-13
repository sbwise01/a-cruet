package com.bradandmarsha.acruet.admin.rest;

import jakarta.ws.rs.ApplicationPath;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * JAX-RS wiring for the administrator WAR.
 */
@ApplicationPath("/")
public class AdminJaxRsApplication extends ResourceConfig {

    public AdminJaxRsApplication() {
        packages(
                "com.bradandmarsha.acruet.rest",
                "com.bradandmarsha.acruet.auth",
                "com.bradandmarsha.acruet.admin.rest"
        );
    }
}
