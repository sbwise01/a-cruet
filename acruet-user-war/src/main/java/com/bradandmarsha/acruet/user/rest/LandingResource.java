package com.bradandmarsha.acruet.user.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Placeholder landing page until OIDC and ledger features are implemented.
 */
@Path("/")
public class LandingResource {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String index() {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>a-cruet</title>
                  <style>
                    body { font-family: system-ui, sans-serif; margin: 2rem; line-height: 1.5; }
                    h1 { margin-bottom: 0.25rem; }
                    p { color: #444; max-width: 40rem; }
                  </style>
                </head>
                <body>
                  <h1>a-cruet</h1>
                  <p>Envelope budgeting for intentional savings. User application scaffold (Phase 1).</p>
                  <p>Sign-in, signup, and ledger features arrive in later rollout phases.</p>
                </body>
                </html>
                """;
    }
}
