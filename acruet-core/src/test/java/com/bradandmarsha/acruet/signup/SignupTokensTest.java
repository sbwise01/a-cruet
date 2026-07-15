package com.bradandmarsha.acruet.signup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SignupTokensTest {

    @Test
    void hashIsDeterministic() {
        assertEquals(SignupTokens.hash("example-token"), SignupTokens.hash("example-token"));
    }

    @Test
    void newTokenIsUnique() {
        assertNotEquals(SignupTokens.newToken(), SignupTokens.newToken());
    }
}
