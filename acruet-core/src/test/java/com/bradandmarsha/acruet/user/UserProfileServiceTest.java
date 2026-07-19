package com.bradandmarsha.acruet.user;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserProfileServiceTest {

    @Test
    void rejectsBlankDisplayName() {
        Optional<String> error = UserProfileService.validate(
                new UserProfileService.ProfileUpdate(" ", "555-0100", "1 Main St", false));
        assertTrue(error.isPresent());
        assertEquals("Full name is required.", error.get());
    }

    @Test
    void rejectsBlankPhone() {
        Optional<String> error = UserProfileService.validate(
                new UserProfileService.ProfileUpdate("Alice Example", " ", "1 Main St", false));
        assertTrue(error.isPresent());
        assertEquals("Phone number is required.", error.get());
    }

    @Test
    void acceptsCompleteProfile() {
        Optional<String> error = UserProfileService.validate(
                new UserProfileService.ProfileUpdate("Alice Example", "555-0100", "1 Main St", true));
        assertTrue(error.isEmpty());
    }
}
