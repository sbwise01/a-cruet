package com.bradandmarsha.acruet.keycloak;

import com.bradandmarsha.acruet.config.KeycloakAdminSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Keycloak Admin REST API via {@code acruet-admin} client credentials (Phase 6).
 */
public final class KeycloakAdminClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final KeycloakAdminSettings settings;

    public KeycloakAdminClient(KeycloakAdminSettings settings) {
        this.settings = settings;
    }

    public ProvisionedUser provisionUser(String email, String fullName) {
        if (!settings.isConfigured()) {
            throw new KeycloakAdminException("Keycloak Admin API is not configured");
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (findUserIdByEmail(normalizedEmail).isPresent()) {
            throw new KeycloakAdminException("A Keycloak user already exists for this email.");
        }

        String temporaryPassword = newTemporaryPassword();
        NameParts nameParts = splitName(fullName);
        String userId = createUser(normalizedEmail, nameParts);
        setTemporaryPassword(userId, temporaryPassword);
        return new ProvisionedUser(userId, temporaryPassword);
    }

    private String createUser(String email, NameParts nameParts) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("username", email);
        payload.put("email", email);
        payload.put("firstName", nameParts.firstName());
        payload.put("lastName", nameParts.lastName());
        payload.put("enabled", true);
        payload.put("emailVerified", true);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(settings.adminApiBaseUrl() + "/users"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201) {
                return userIdFromLocation(response.headers().firstValue("Location").orElse(null));
            }
            if (response.statusCode() == 409) {
                throw new KeycloakAdminException("A Keycloak user already exists for this email.");
            }
            throw new KeycloakAdminException(adminApiFailure("Keycloak user creation", response.statusCode()));
        } catch (KeycloakAdminException exception) {
            throw exception;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new KeycloakAdminException("Keycloak user creation interrupted", interrupted);
        } catch (Exception exception) {
            throw new KeycloakAdminException("Keycloak user creation failed", exception);
        }
    }

    private void setTemporaryPassword(String userId, String temporaryPassword) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("type", "password");
        payload.put("value", temporaryPassword);
        payload.put("temporary", true);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(settings.adminApiBaseUrl() + "/users/" + userId + "/reset-password"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken())
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new KeycloakAdminException(
                        adminApiFailure("Keycloak password reset", response.statusCode()));
            }
        } catch (KeycloakAdminException exception) {
            throw exception;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new KeycloakAdminException("Keycloak password reset interrupted", interrupted);
        } catch (Exception exception) {
            throw new KeycloakAdminException("Keycloak password reset failed", exception);
        }
    }

    private Optional<String> findUserIdByEmail(String email) {
        String query = "email=" + encode(email) + "&exact=true";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(settings.adminApiBaseUrl() + "/users?" + query))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken())
                .GET()
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new KeycloakAdminException(
                        adminApiFailure("Keycloak user lookup", response.statusCode()));
            }
            JsonNode users = JSON.readTree(response.body());
            if (!users.isArray() || users.isEmpty()) {
                return Optional.empty();
            }
            JsonNode first = users.get(0);
            JsonNode id = first.get("id");
            if (id == null || id.asText().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(id.asText());
        } catch (KeycloakAdminException exception) {
            throw exception;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new KeycloakAdminException("Keycloak user lookup interrupted", interrupted);
        } catch (Exception exception) {
            throw new KeycloakAdminException("Keycloak user lookup failed", exception);
        }
    }

    private String accessToken() {
        String body = Map.of(
                        "grant_type", "client_credentials",
                        "client_id", settings.clientId(),
                        "client_secret", settings.clientSecret())
                .entrySet()
                .stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(settings.tokenEndpoint()))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new KeycloakAdminException(
                        adminApiFailure("Keycloak token endpoint", response.statusCode()));
            }
            JsonNode tokenResponse = JSON.readTree(response.body());
            JsonNode accessToken = tokenResponse.get("access_token");
            if (accessToken == null || accessToken.asText().isBlank()) {
                throw new KeycloakAdminException("Keycloak token response missing access_token");
            }
            return accessToken.asText();
        } catch (KeycloakAdminException exception) {
            throw exception;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new KeycloakAdminException("Keycloak token request interrupted", interrupted);
        } catch (Exception exception) {
            throw new KeycloakAdminException("Keycloak token request failed", exception);
        }
    }

    static NameParts splitName(String fullName) {
        String trimmed = fullName == null ? "" : fullName.trim();
        if (trimmed.isBlank()) {
            return new NameParts("Applicant", "User");
        }
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            return new NameParts(trimmed, "User");
        }
        String first = trimmed.substring(0, space).trim();
        String last = trimmed.substring(space + 1).trim();
        if (first.isBlank()) {
            first = "Applicant";
        }
        if (last.isBlank()) {
            last = "User";
        }
        return new NameParts(first, last);
    }

    static String newTemporaryPassword() {
        StringBuilder builder = new StringBuilder(16);
        for (int index = 0; index < 16; index++) {
            builder.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return builder.toString();
    }

    private static String userIdFromLocation(String location) {
        if (location == null || location.isBlank()) {
            throw new KeycloakAdminException("Keycloak user creation missing Location header");
        }
        int slash = location.lastIndexOf('/');
        if (slash < 0 || slash == location.length() - 1) {
            throw new KeycloakAdminException("Keycloak user creation returned invalid Location");
        }
        return location.substring(slash + 1);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String adminApiFailure(String action, int statusCode) {
        if (statusCode == 403) {
            return action
                    + " returned HTTP 403. Service-account roles are assigned, but Keycloak"
                    + " is still denying Admin API access. On client acruet-admin: Client scopes"
                    + " → acruet-admin-dedicated → Scope tab → Full scope allowed ON, or assign"
                    + " realm-management roles there; fallback: realm-admin on service account.";
        }
        return action + " returned HTTP " + statusCode;
    }

    record NameParts(String firstName, String lastName) {
    }
}
