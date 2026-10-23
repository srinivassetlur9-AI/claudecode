package com.lms.identity.service;

import com.lms.identity.config.LmsProperties;
import com.lms.identity.domain.TokenPurpose;
import com.lms.identity.domain.VerificationToken;
import com.lms.identity.repo.VerificationTokenRepository;
import com.lms.identity.security.Hashing;
import com.lms.identity.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Single-use, expiring secrets. Only hashes are stored; issuing a new one invalidates older ones of that purpose. */
@Service
public class TokenService {
    private static final int MAX_OTP_ATTEMPTS = 5;

    private final VerificationTokenRepository repo;
    private final LmsProperties props;

    public TokenService(VerificationTokenRepository repo, LmsProperties props) {
        this.repo = repo;
        this.props = props;
    }

    /** Link token (email verification, password reset). Returns the raw value to put in the link. */
    @Transactional
    public String issueLinkToken(Long userId, TokenPurpose purpose) {
        Duration ttl = purpose == TokenPurpose.EMAIL_VERIFY
                ? Duration.ofHours(props.tokens().emailVerifyHours())
                : Duration.ofMinutes(props.tokens().resetMinutes());
        String raw = Hashing.randomToken(32);
        store(userId, purpose, Hashing.sha256(raw), ttl);
        return raw;
    }

    /** Consumes a link token and returns the user it belongs to. */
    @Transactional(noRollbackFor = ApiException.class)
    public Long consumeLinkToken(String raw, TokenPurpose purpose) {
        VerificationToken t = repo.findByTokenHashAndPurpose(Hashing.sha256(raw), purpose)
                .filter(x -> x.getUsedAt() == null && x.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN", "This link is invalid or has expired"));
        t.setUsedAt(Instant.now());
        return t.getUserId();
    }

    @Transactional
    public String issueOtp(Long userId) {
        String otp = Hashing.randomOtp();
        store(userId, TokenPurpose.MOBILE_OTP, otpHash(userId, otp), Duration.ofMinutes(props.tokens().otpMinutes()));
        return otp;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void consumeOtp(Long userId, String otp) {
        VerificationToken t = repo.findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByIdDesc(userId, TokenPurpose.MOBILE_OTP)
                .filter(x -> x.getExpiresAt().isAfter(Instant.now()) && x.getAttempts() < MAX_OTP_ATTEMPTS)
                .orElseThrow(() -> ApiException.badRequest("INVALID_OTP", "The code is invalid or has expired"));
        if (!otpHash(userId, otp).equals(t.getTokenHash())) {
            t.setAttempts(t.getAttempts() + 1);
            throw ApiException.badRequest("INVALID_OTP", "The code is invalid or has expired");
        }
        t.setUsedAt(Instant.now());
    }

    private void store(Long userId, TokenPurpose purpose, String hash, Duration ttl) {
        repo.invalidateOutstanding(userId, purpose, Instant.now());
        VerificationToken t = new VerificationToken();
        t.setUserId(userId);
        t.setPurpose(purpose);
        t.setTokenHash(hash);
        t.setExpiresAt(Instant.now().plus(ttl));
        repo.save(t);
    }

    // A 6-digit code has only a million values, so bind the hash to the user.
    private static String otpHash(Long userId, String otp) {
        return Hashing.sha256(userId + ":" + otp);
    }
}
