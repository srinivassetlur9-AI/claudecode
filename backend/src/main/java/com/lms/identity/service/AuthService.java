package com.lms.identity.service;

import com.lms.identity.config.LmsProperties;
import com.lms.identity.domain.*;
import com.lms.identity.repo.OrganizationRepository;
import com.lms.identity.repo.UserProfileRepository;
import com.lms.identity.repo.UserRepository;
import com.lms.identity.security.JwtService;
import com.lms.identity.web.ApiException;
import com.lms.identity.web.Dtos.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final OrganizationRepository orgs;
    private final UserProfileRepository profiles;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final SessionService sessions;
    private final JwtService jwt;
    private final NotificationSender notifier;
    private final LmsProperties props;
    private final ObjectProvider<DeviceGate> deviceGate;
    // Used to burn the same time on unknown emails as on real ones, so response time does not reveal accounts.
    private final String dummyHash;

    public AuthService(UserRepository users, OrganizationRepository orgs, UserProfileRepository profiles,
                       PasswordEncoder encoder, TokenService tokens, SessionService sessions, JwtService jwt,
                       NotificationSender notifier, LmsProperties props, ObjectProvider<DeviceGate> deviceGate) {
        this.users = users;
        this.orgs = orgs;
        this.profiles = profiles;
        this.encoder = encoder;
        this.tokens = tokens;
        this.sessions = sessions;
        this.jwt = jwt;
        this.notifier = notifier;
        this.props = props;
        this.deviceGate = deviceGate;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    // ---------- registration & verification ----------

    @Transactional
    public MessageResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        PasswordPolicy.check(req.password(), email);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        String mobile = blankToNull(req.mobile());
        if (mobile != null && users.existsByMobile(mobile)) {
            throw ApiException.conflict("MOBILE_TAKEN", "An account with this mobile number already exists");
        }

        User u = new User();
        u.setEmail(email);
        u.setMobile(mobile);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setFirstName(req.firstName().trim());
        u.setLastName(req.lastName().trim());
        u.setStatus(UserStatus.PENDING);

        switch (req.accountType()) {
            case LEARNER -> u.replaceRoles(Set.of(Role.LEARNER));
            case INSTRUCTOR -> u.replaceRoles(Set.of(Role.INSTRUCTOR));
            case ORGANIZATION -> {
                String orgName = blankToNull(req.organizationName());
                if (orgName == null) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "organizationName is required for organization accounts");
                }
                Organization org = new Organization();
                org.setName(orgName.trim());
                org.setType("ORGANIZATION");
                u.setOrganizationId(orgs.save(org).getId());
                u.replaceRoles(Set.of(Role.ORG_ADMIN));
            }
        }
        try {
            users.saveAndFlush(u);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email or mobile already exists");
        }
        UserProfile profile = new UserProfile();
        profile.setUserId(u.getId());
        profiles.save(profile);

        sendEmailVerification(u);
        if (mobile != null) notifier.sendMobileOtp(u, tokens.issueOtp(u.getId()));
        return new MessageResponse("Account created. Check your email to verify it.");
    }

    @Transactional
    public MessageResponse verifyEmail(String token) {
        Long userId = tokens.consumeLinkToken(token, TokenPurpose.EMAIL_VERIFY);
        User u = users.findById(userId).orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN", "This link is invalid or has expired"));
        u.setEmailVerified(true);
        if (u.getStatus() == UserStatus.PENDING) u.setStatus(UserStatus.ACTIVE); // activation
        return new MessageResponse("Email verified. You can now sign in.");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public MessageResponse verifyMobile(VerifyMobileRequest req) {
        User u = users.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> ApiException.badRequest("INVALID_OTP", "The code is invalid or has expired"));
        tokens.consumeOtp(u.getId(), req.otp());
        u.setMobileVerified(true);
        return new MessageResponse("Mobile number verified.");
    }

    /** Always answers the same way so it cannot be used to discover which emails are registered. */
    @Transactional
    public MessageResponse resendVerification(ResendRequest req) {
        users.findByEmailIgnoreCase(req.email().trim()).ifPresent(u -> {
            if (req.channel() == Channel.EMAIL && !u.isEmailVerified()) {
                sendEmailVerification(u);
            } else if (req.channel() == Channel.MOBILE && u.getMobile() != null && !u.isMobileVerified()) {
                notifier.sendMobileOtp(u, tokens.issueOtp(u.getId()));
            }
        });
        return new MessageResponse("If the account exists and needs verification, a new code has been sent.");
    }

    // ---------- login / refresh / logout ----------

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse login(LoginRequest req, SessionService.ClientInfo client) {
        User u = users.findByEmailIgnoreCase(req.email().trim()).orElse(null);
        if (u == null) {
            encoder.matches(req.password(), dummyHash);
            throw invalidCredentials();
        }
        Instant now = Instant.now();
        if (u.isLocked(now)) {
            throw new ApiException(org.springframework.http.HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "Too many failed attempts. Try again later.");
        }
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            u.setFailedAttempts(u.getFailedAttempts() + 1);
            if (u.getFailedAttempts() >= props.security().maxFailedAttempts()) {
                u.setLockedUntil(now.plus(Duration.ofMinutes(props.security().lockoutMinutes())));
                u.setFailedAttempts(0);
            }
            throw invalidCredentials();
        }
        // Only reveal account state once the password is proven correct.
        if (u.getStatus() == UserStatus.DISABLED) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "This account has been disabled");
        }
        if (u.getStatus() == UserStatus.PENDING) {
            throw ApiException.forbidden("EMAIL_NOT_VERIFIED", "Verify your email before signing in");
        }
        SessionService.ClientInfo info = new SessionService.ClientInfo(
                client.deviceName() != null ? client.deviceName() : req.deviceName(), client.userAgent(), client.ip(),
                client.deviceKey());
        DeviceGate gate = deviceGate.getIfAvailable();
        Long deviceId = gate == null ? null : gate.admit(u, info); // throws when the device limit is reached
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        u.setLastLoginAt(now);
        return issue(u, sessions.create(u.getId(), info, deviceId));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse refresh(String refreshToken, SessionService.ClientInfo client) {
        SessionService.Issued next = sessions.rotate(refreshToken, client);
        User u = users.findById(next.session().getUserId()).orElse(null);
        if (u == null || u.getStatus() != UserStatus.ACTIVE) {
            sessions.revokeAll(next.session().getUserId(), "ADMIN");
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session is no longer valid");
        }
        DeviceGate gate = deviceGate.getIfAvailable();
        if (gate != null && next.session().getDeviceId() != null && !gate.isActive(next.session().getDeviceId())) {
            sessions.revoke(u.getId(), next.session().getId(), "DEVICE_DEACTIVATED");
            throw ApiException.unauthorized("DEVICE_DEACTIVATED", "This device has been deactivated");
        }
        return issue(u, next);
    }

    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) sessions.revokeByRefreshToken(refreshToken);
    }

    // ---------- passwords ----------

    /** Always answers the same way so it cannot be used to discover which emails are registered. */
    @Transactional
    public MessageResponse forgotPassword(String email) {
        users.findByEmailIgnoreCase(email.trim()).ifPresent(u -> {
            if (u.getStatus() != UserStatus.DISABLED) {
                String raw = tokens.issueLinkToken(u.getId(), TokenPurpose.PASSWORD_RESET);
                notifier.sendPasswordReset(u, props.frontendBaseUrl() + "/reset-password?token=" + raw);
            }
        });
        return new MessageResponse("If an account exists for that email, a reset link has been sent.");
    }

    @Transactional
    public MessageResponse resetPassword(String token, String newPassword) {
        Long userId = tokens.consumeLinkToken(token, TokenPurpose.PASSWORD_RESET);
        User u = users.findById(userId).orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN", "This link is invalid or has expired"));
        PasswordPolicy.check(newPassword, u.getEmail());
        u.setPasswordHash(encoder.encode(newPassword));
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        sessions.revokeAll(u.getId(), "PASSWORD_CHANGE");
        return new MessageResponse("Password updated. Sign in with your new password.");
    }

    @Transactional
    public MessageResponse changePassword(Long userId, ChangePasswordRequest req) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest("WRONG_PASSWORD", "Current password is incorrect");
        }
        PasswordPolicy.check(req.newPassword(), u.getEmail());
        u.setPasswordHash(encoder.encode(req.newPassword()));
        sessions.revokeAll(u.getId(), "PASSWORD_CHANGE");
        return new MessageResponse("Password changed. Please sign in again.");
    }

    // ---------- helpers ----------

    private void sendEmailVerification(User u) {
        String raw = tokens.issueLinkToken(u.getId(), TokenPurpose.EMAIL_VERIFY);
        notifier.sendEmailVerification(u, props.frontendBaseUrl() + "/verify-email?token=" + raw);
    }

    private TokenResponse issue(User u, SessionService.Issued s) {
        return new TokenResponse(jwt.issueAccessToken(u, s.session().getId()), s.refreshToken(), "Bearer",
                jwt.accessTtlSeconds(), UserView.of(u));
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Email or password is incorrect");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
