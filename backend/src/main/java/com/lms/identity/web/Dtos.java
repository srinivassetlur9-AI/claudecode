package com.lms.identity.web;

import com.lms.identity.domain.Role;
import com.lms.identity.domain.User;
import com.lms.identity.domain.UserStatus;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class Dtos {
    private Dtos() {}

    public static final String MOBILE_RE = "^\\+?[0-9]{8,15}$";

    public enum AccountType { LEARNER, INSTRUCTOR, ORGANIZATION }
    public enum Channel { EMAIL, MOBILE }

    // ---- auth ----
    public record RegisterRequest(
            @NotNull AccountType accountType,
            @NotBlank @Email @Size(max = 254) String email,
            @Pattern(regexp = MOBILE_RE, message = "must be 8-15 digits, optional leading +") String mobile,
            @NotBlank @Size(min = 10, max = 128) String password,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Size(max = 200) String organizationName) {}

    public record VerifyEmailRequest(@NotBlank String token) {}
    public record VerifyMobileRequest(@NotBlank @Email String email, @NotBlank @Pattern(regexp = "^[0-9]{6}$") String otp) {}
    public record ResendRequest(@NotBlank @Email String email, @NotNull Channel channel) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max = 128) String password,
                               @Size(max = 150) String deviceName) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record LogoutRequest(String refreshToken) {}
    public record ForgotPasswordRequest(@NotBlank @Email String email) {}
    public record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min = 10, max = 128) String newPassword) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 10, max = 128) String newPassword) {}
    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, UserView user) {}
    public record MessageResponse(String message) {}

    // ---- users / profile ----
    public record UserView(Long id, Long organizationId, String email, String mobile, String firstName, String lastName,
                           UserStatus status, boolean emailVerified, boolean mobileVerified, String language,
                           Set<Role> roles, Instant lastLoginAt, Instant createdAt) {
        public static UserView of(User u) {
            return new UserView(u.getId(), u.getOrganizationId(), u.getEmail(), u.getMobile(), u.getFirstName(),
                    u.getLastName(), u.getStatus(), u.isEmailVerified(), u.isMobileVerified(), u.getLanguage(),
                    u.roles(), u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    public record ProfileView(UserView user, String headline, String bio, String avatarUrl, String country, String timezone) {}

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Pattern(regexp = MOBILE_RE, message = "must be 8-15 digits, optional leading +") String mobile,
            @Size(max = 200) String headline,
            @Size(max = 2000) String bio,
            @Size(max = 500) @Pattern(regexp = "^(https://.*)?$", message = "must be an https URL") String avatarUrl,
            @Pattern(regexp = "^([A-Z]{2})?$", message = "ISO 3166 alpha-2 code") String country,
            @Size(max = 60) String timezone) {}

    public record UpdateSettingsRequest(
            @NotBlank @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "e.g. en, hi, en-IN") String language) {}

    public record SessionView(Long id, String deviceName, String ipAddress, Instant lastSeenAt, Instant createdAt, boolean current) {}

    // ---- admin ----
    public record SetRolesRequest(@NotEmpty Set<Role> roles) {}
    public record SetStatusRequest(@NotNull UserStatus status) {}
    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotEmpty Set<Role> roles,
            Long organizationId) {}
    public record PageView<T>(List<T> items, int page, int size, long total) {}

    // ---- contracts for other modules ----
    public record TokenValidation(Long userId, Long organizationId, Set<Role> roles, Long sessionId) {}
    public record AuthzRequest(@NotBlank String action, String resourceType, Long resourceId, Long organizationId, Long ownerId) {}
    public record AuthzResponse(boolean allowed) {}
    public record EntitlementView(String type, String resourceRef, String source, Instant validUntil) {}
}
