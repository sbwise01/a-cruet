package com.bradandmarsha.acruet.signup;

import com.bradandmarsha.acruet.auth.OidcSettings;
import com.bradandmarsha.acruet.config.SmtpSettings;
import com.bradandmarsha.acruet.mail.MailSender;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Public applicant signup and email verification (Phase 5).
 */
public final class SignupService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final SignupRepository repository;
    private final SignupRateLimiter rateLimiter;
    private final MailSender mailSender;
    private final String baseUrl;

    public SignupService(
            SignupRepository repository,
            SignupRateLimiter rateLimiter,
            MailSender mailSender,
            String baseUrl) {
        this.repository = repository;
        this.rateLimiter = rateLimiter;
        this.mailSender = mailSender;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public static SignupService fromEnvironment() {
        OidcSettings oidc = OidcSettings.fromEnvironment();
        return new SignupService(
                new SignupRepository(),
                new SignupRateLimiter(new SignupRepository()),
                new MailSender(SmtpSettings.fromEnvironment()),
                oidc.baseUrl());
    }

    public SubmitResult submit(SignupRequest request, String applicantIp, Instant now) {
        try {
            Optional<String> validationError = validate(request);
            if (validationError.isPresent()) {
                rateLimiter.recordAttempt(request.email(), applicantIp);
                return SubmitResult.error(validationError.get());
            }

            SignupRateLimiter.RateLimitResult rateLimit =
                    rateLimiter.check(request.email(), applicantIp, now);
            if (!rateLimit.permitted()) {
                rateLimiter.recordAttempt(request.email(), applicantIp);
                return SubmitResult.error(rateLimit.message());
            }

            Optional<SignupApplication> latest = repository.findLatestByEmail(request.email());
            SignupPolicy.Decision decision = SignupPolicy.evaluate(latest, now);
            if (decision != SignupPolicy.Decision.ALLOW) {
                rateLimiter.recordAttempt(request.email(), applicantIp);
                return SubmitResult.error(policyMessage(decision));
            }

            String token = SignupTokens.newToken();
            UUID id = UUID.randomUUID();
            repository.insertApplication(
                    id,
                    request.email(),
                    request.fullName(),
                    request.reason(),
                    request.phone(),
                    request.mailingAddress(),
                    SignupTokens.hash(token),
                    now.plus(24, ChronoUnit.HOURS),
                    applicantIp);
            rateLimiter.recordAttempt(request.email(), applicantIp);

            String verifyUrl = baseUrl + "/signup/verify?token=" + token;
            mailSender.send(
                    request.email(),
                    "Verify your a-cruet application",
                    """
                            Hello %s,

                            Thanks for applying to a-cruet. Verify your email to queue your application for admin review:

                            %s

                            This link expires in 24 hours. If you did not request this, you can ignore this message.
                            """.formatted(request.fullName(), verifyUrl));

            return SubmitResult.verificationSent(request.email());
        } catch (Exception exception) {
            return SubmitResult.error("Signup is temporarily unavailable. Please try again later.");
        }
    }

    public VerifyResult verify(String token, Instant now) {
        if (token == null || token.isBlank()) {
            return VerifyResult.INVALID;
        }
        try {
            boolean updated = repository.verifyByTokenHash(SignupTokens.hash(token.trim()), now);
            return updated ? VerifyResult.PENDING_APPROVAL : VerifyResult.INVALID;
        } catch (Exception exception) {
            return VerifyResult.INVALID;
        }
    }

    private static Optional<String> validate(SignupRequest request) {
        if (isBlank(request.fullName())) {
            return Optional.of("Name is required.");
        }
        if (isBlank(request.email()) || !EMAIL_PATTERN.matcher(request.email().trim()).matches()) {
            return Optional.of("A valid email address is required.");
        }
        if (isBlank(request.reason())) {
            return Optional.of("Please include a short reason for your request.");
        }
        if (isBlank(request.phone())) {
            return Optional.of("Phone number is required.");
        }
        if (isBlank(request.mailingAddress())) {
            return Optional.of("Mailing address is required.");
        }
        return Optional.empty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String policyMessage(SignupPolicy.Decision decision) {
        return switch (decision) {
            case IN_PROGRESS -> "An application for this email is already in progress.";
            case COOLDOWN -> "This email must wait "
                    + SignupPolicy.COOLDOWN_DAYS
                    + " days after a rejection before re-applying.";
            case BLOCKED -> "This email is blocked from re-applying. Contact an administrator.";
            case ALLOW -> "";
        };
    }

    public record SignupRequest(
            String fullName, String email, String reason, String phone, String mailingAddress) {
    }

    public record SubmitResult(boolean success, String message, Optional<String> email) {
        static SubmitResult verificationSent(String email) {
            return new SubmitResult(
                    true,
                    "Check your email for a verification link. After verifying, your application will await admin approval.",
                    Optional.of(email));
        }

        static SubmitResult error(String message) {
            return new SubmitResult(false, message, Optional.empty());
        }
    }

    public enum VerifyResult {
        PENDING_APPROVAL,
        INVALID
    }
}
