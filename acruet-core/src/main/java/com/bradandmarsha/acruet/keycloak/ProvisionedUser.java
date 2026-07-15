package com.bradandmarsha.acruet.keycloak;

/**
 * Result of provisioning a Keycloak user on signup approval.
 */
public record ProvisionedUser(String keycloakUserId, String temporaryPassword) {
}
