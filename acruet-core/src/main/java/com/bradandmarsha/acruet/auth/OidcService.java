package com.bradandmarsha.acruet.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTParser;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Discovers Keycloak endpoints and performs authorization-code token exchange.
 *
 * <p>Java {@link HttpClient} defaults to HTTP/2 and connection reuse. Envoy often
 * RSTs those sockets while {@code curl} (HTTP/1.1, new connection) succeeds. This
 * client stays on HTTP/1.1 and retries transient I/O.</p>
 */
public final class OidcService {

    private static final int MAX_ATTEMPTS = 3;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ConcurrentHashMap<String, OidcService> INSTANCES = new ConcurrentHashMap<>();

    private final OidcSettings settings;
    private final Map<String, OidcDiscovery> discoveryCache = new ConcurrentHashMap<>();

    public OidcService(OidcSettings settings) {
        this.settings = settings;
    }

    /**
     * Process-wide instance so {@code /auth/login} and callback share discovery
     * cache instead of fetching Keycloak on every request.
     */
    public static OidcService forSettings(OidcSettings settings) {
        String key = settings.issuer() + "|" + settings.baseUrl() + "|" + settings.clientId()
                + "|" + settings.requireAdminRole();
        return INSTANCES.computeIfAbsent(key, ignored -> new OidcService(settings));
    }

    public String beginAuthorizationUri(String state) {
        OidcDiscovery discovery = discovery();
        String query = Map.of(
                        "client_id", settings.clientId(),
                        "response_type", "code",
                        "scope", "openid profile email",
                        "redirect_uri", settings.callbackUrl(),
                        "state", state)
                .entrySet()
                .stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return discovery.authorizationEndpoint() + "?" + query;
    }

    public OidcUser completeAuthorization(String code) {
        OidcDiscovery discovery = discovery();
        String body = Map.of(
                        "grant_type", "authorization_code",
                        "code", code,
                        "redirect_uri", settings.callbackUrl(),
                        "client_id", settings.clientId(),
                        "client_secret", settings.clientSecret())
                .entrySet()
                .stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(discovery.tokenEndpoint()))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = sendWithRetry(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Token endpoint returned HTTP " + response.statusCode());
            }
            JsonNode tokenResponse = JSON.readTree(response.body());
            String accessToken = textOrNull(tokenResponse, "access_token");
            if (accessToken == null) {
                throw new IllegalStateException("Token response missing access_token");
            }
            return userFromAccessToken(accessToken);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Token exchange interrupted", interrupted);
        } catch (Exception exception) {
            throw new IllegalStateException("Token exchange failed", exception);
        }
    }

    public String logoutRedirectUri() {
        OidcDiscovery discovery = discovery();
        String query = Map.of(
                        "client_id", settings.clientId(),
                        "post_logout_redirect_uri", settings.baseUrl() + "/")
                .entrySet()
                .stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return discovery.logoutEndpoint() + "?" + query;
    }

    public static String newState() {
        return UUID.randomUUID().toString();
    }

    static OidcUser userFromAccessToken(String accessToken) throws Exception {
        var claims = JWTParser.parse(accessToken).getJWTClaimsSet();
        Set<String> roles = new LinkedHashSet<>();
        Object realmAccess = claims.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> realmAccessMap) {
            Object roleNames = realmAccessMap.get("roles");
            if (roleNames instanceof Iterable<?> iterable) {
                for (Object role : iterable) {
                    if (role != null) {
                        roles.add(role.toString());
                    }
                }
            }
        }
        return new OidcUser(
                claims.getSubject(),
                stringClaim(claims.getClaim("preferred_username")),
                stringClaim(claims.getClaim("email")),
                stringClaim(claims.getClaim("given_name")),
                stringClaim(claims.getClaim("family_name")),
                roles);
    }

    private OidcDiscovery discovery() {
        return discoveryCache.computeIfAbsent(settings.issuer(), ignored -> loadDiscovery());
    }

    private OidcDiscovery loadDiscovery() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(settings.discoveryUrl()))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try {
            HttpResponse<String> response = sendWithRetry(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Discovery returned HTTP " + response.statusCode());
            }
            JsonNode document = JSON.readTree(response.body());
            String authorization = requiredText(document, "authorization_endpoint");
            String token = requiredText(document, "token_endpoint");
            String logout = requiredText(document, "end_session_endpoint");
            return new OidcDiscovery(authorization, token, logout);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Discovery interrupted", interrupted);
        } catch (Exception exception) {
            throw new IllegalStateException("OIDC discovery failed", exception);
        }
    }

    private static HttpResponse<String> sendWithRetry(HttpRequest request)
            throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException exception) {
                last = exception;
                if (attempt == MAX_ATTEMPTS || !isTransientIo(exception)) {
                    throw exception;
                }
            }
        }
        throw last;
    }

    static boolean isTransientIo(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof HttpTimeoutException
                    || current instanceof ConnectException
                    || current instanceof SocketException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String requiredText(JsonNode node, String field) {
        String value = textOrNull(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Discovery document missing " + field);
        }
        return value;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static String stringClaim(Object value) {
        return value == null ? null : value.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    record OidcDiscovery(String authorizationEndpoint, String tokenEndpoint, String logoutEndpoint) {
    }
}
