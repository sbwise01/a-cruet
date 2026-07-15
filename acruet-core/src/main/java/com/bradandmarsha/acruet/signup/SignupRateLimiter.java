package com.bradandmarsha.acruet.signup;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Homelab signup throttling defaults (PRODUCT.md #50).
 */
public final class SignupRateLimiter {

    public static final int MAX_IP_ATTEMPTS_PER_HOUR = 5;
    public static final int MAX_EMAIL_ATTEMPTS_PER_DAY = 3;

    private final SignupRepository repository;

    public SignupRateLimiter(SignupRepository repository) {
        this.repository = repository;
    }

    public RateLimitResult check(String email, String ipAddress, Instant now) throws Exception {
        int ipAttempts = repository.countIpAttempts(ipAddress, now.minus(1, ChronoUnit.HOURS));
        if (ipAttempts >= MAX_IP_ATTEMPTS_PER_HOUR) {
            return RateLimitResult.ipLimited();
        }
        int emailAttempts = repository.countEmailAttempts(email, now.minus(1, ChronoUnit.DAYS));
        if (emailAttempts >= MAX_EMAIL_ATTEMPTS_PER_DAY) {
            return RateLimitResult.emailLimited();
        }
        return RateLimitResult.notLimited();
    }

    public void recordAttempt(String email, String ipAddress) throws Exception {
        repository.recordAttempt(email, ipAddress);
    }

    public record RateLimitResult(boolean permitted, String message) {
        static RateLimitResult notLimited() {
            return new RateLimitResult(true, "");
        }

        static RateLimitResult ipLimited() {
            return new RateLimitResult(false, "Too many signup attempts from your network. Try again later.");
        }

        static RateLimitResult emailLimited() {
            return new RateLimitResult(false, "Too many signup attempts for this email. Try again tomorrow.");
        }
    }
}
