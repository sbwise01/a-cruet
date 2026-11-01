package com.bradandmarsha.acruet.keys;

import com.bradandmarsha.acruet.user.AcruetUser;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * WebAuthn Level 3 publicKey option builders (platform authenticator + PRF).
 */
public final class WebAuthnOptions {

    public static final int CHALLENGE_BYTES = 32;
    public static final int TIMEOUT_MS = 120_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    private WebAuthnOptions() {
    }

    public static byte[] newChallenge() {
        byte[] challenge = new byte[CHALLENGE_BYTES];
        RANDOM.nextBytes(challenge);
        return challenge;
    }

    public static String encode(byte[] value) {
        return B64URL.encodeToString(value);
    }

    public static Map<String, Object> attestation(WebAuthnSettings settings, AcruetUser user, byte[] challenge) {
        Map<String, Object> rp = new LinkedHashMap<>();
        rp.put("id", settings.rpId());
        rp.put("name", settings.rpName());

        Map<String, Object> webauthnUser = new LinkedHashMap<>();
        webauthnUser.put("id", encode(uuidBytes(user.id())));
        webauthnUser.put("name", user.email());
        webauthnUser.put("displayName", user.displayName());

        Map<String, Object> authenticatorSelection = new LinkedHashMap<>();
        authenticatorSelection.put("authenticatorAttachment", "platform");
        authenticatorSelection.put("residentKey", "required");
        authenticatorSelection.put("requireResidentKey", true);
        authenticatorSelection.put("userVerification", "required");

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("rp", rp);
        options.put("user", webauthnUser);
        options.put("challenge", encode(challenge));
        options.put("pubKeyCredParams", List.of(
                algParam(-7),
                algParam(-257)));
        options.put("timeout", TIMEOUT_MS);
        options.put("authenticatorSelection", authenticatorSelection);
        options.put("attestation", "none");
        options.put("extensions", prfExtensions());
        return options;
    }

    public static Map<String, Object> assertion(
            WebAuthnSettings settings, List<UserUnlockDevice> devices, byte[] challenge) {
        List<Map<String, Object>> allowCredentials = new ArrayList<>(devices.size());
        for (UserUnlockDevice device : devices) {
            Map<String, Object> descriptor = new LinkedHashMap<>();
            descriptor.put("type", "public-key");
            descriptor.put("id", encode(device.credentialId()));
            allowCredentials.add(descriptor);
        }
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("challenge", encode(challenge));
        options.put("timeout", TIMEOUT_MS);
        options.put("rpId", settings.rpId());
        options.put("allowCredentials", allowCredentials);
        options.put("userVerification", "required");
        options.put("extensions", prfExtensions());
        return options;
    }

    public static boolean clientDataMatchesChallenge(String clientDataJsonBase64, byte[] expectedChallenge) {
        byte[] clientData = DeviceWrapPayload.decodeFlexible(clientDataJsonBase64);
        if (clientData == null || expectedChallenge == null) {
            return false;
        }
        String json = new String(clientData, java.nio.charset.StandardCharsets.UTF_8);
        String encoded = encode(expectedChallenge);
        return json.contains("\"challenge\":\"" + encoded + "\"");
    }

    private static Map<String, Object> prfExtensions() {
        Map<String, Object> prfEval = new LinkedHashMap<>();
        prfEval.put("first", encode(WebAuthnSettings.PRF_EVAL_FIRST));
        return Map.of("prf", Map.of("eval", prfEval));
    }

    private static Map<String, Object> algParam(int alg) {
        return Map.of("type", "public-key", "alg", alg);
    }

    private static byte[] uuidBytes(UUID id) {
        return (id.getMostSignificantBits() + ":" + id.getLeastSignificantBits())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
