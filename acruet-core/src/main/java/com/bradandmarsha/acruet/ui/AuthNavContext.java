package com.bradandmarsha.acruet.ui;

/**
 * Data for authenticated user chrome (avatar menu, Phase 9 item 4).
 */
public record AuthNavContext(
        String initials,
        String displayName,
        String email,
        boolean accountLinked,
        boolean keySetupComplete,
        boolean inlineUnlockHome) {
}
