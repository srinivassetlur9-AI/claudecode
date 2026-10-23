package com.lms.identity.service;

import com.lms.identity.config.LmsProperties;
import com.lms.identity.domain.UserSession;
import com.lms.identity.repo.UserSessionRepository;
import com.lms.identity.security.Hashing;
import com.lms.identity.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One session per login/device, with rotating refresh tokens and reuse detection. */
@Service
public class SessionService {
    public record Issued(UserSession session, String refreshToken) {}
    /** deviceKey is the client-generated install id (X-Device-Id); Module 2 only ever stores it hashed. */
    public record ClientInfo(String deviceName, String userAgent, String ip, String deviceKey) {
        public ClientInfo(String deviceName, String userAgent, String ip) {
            this(deviceName, userAgent, ip, null);
        }
    }

    private final UserSessionRepository repo;
    private final Duration refreshTtl;

    public SessionService(UserSessionRepository repo, LmsProperties props) {
        this.repo = repo;
        this.refreshTtl = Duration.ofDays(props.jwt().refreshTtlDays());
    }

    @Transactional
    public Issued create(Long userId, ClientInfo client) {
        return create(userId, client, null);
    }

    @Transactional
    public Issued create(Long userId, ClientInfo client, Long deviceId) {
        String raw = Hashing.randomToken(48);
        Instant now = Instant.now();
        UserSession s = new UserSession();
        s.setUserId(userId);
        s.setDeviceId(deviceId);
        s.setRefreshTokenHash(Hashing.sha256(raw));
        s.setDeviceName(trim(client.deviceName(), 150));
        s.setUserAgent(trim(client.userAgent(), 500));
        s.setIpAddress(trim(client.ip(), 45));
        s.setLastSeenAt(now);
        s.setExpiresAt(now.plus(refreshTtl));
        return new Issued(repo.save(s), raw);
    }

    /**
     * Rotates a refresh token. Presenting an already-rotated token means it was stolen or replayed,
     * so every session of that user is revoked.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Issued rotate(String rawRefresh, ClientInfo client) {
        UserSession old = repo.findByRefreshTokenHash(Hashing.sha256(rawRefresh))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session is no longer valid"));
        Instant now = Instant.now();
        if (old.getRevokedAt() != null) {
            if ("ROTATED".equals(old.getRevokedReason())) {
                repo.revokeAllForUser(old.getUserId(), now, "REUSE_DETECTED");
            }
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session is no longer valid");
        }
        if (!old.getExpiresAt().isAfter(now)) {
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session has expired");
        }
        old.setRevokedAt(now);
        old.setRevokedReason("ROTATED");
        // Keep the device label across rotations.
        return create(old.getUserId(), new ClientInfo(old.getDeviceName(), client.userAgent(), client.ip()), old.getDeviceId());
    }

    @Transactional
    public void revokeByRefreshToken(String rawRefresh) {
        repo.findByRefreshTokenHash(Hashing.sha256(rawRefresh)).ifPresent(s -> {
            if (s.getRevokedAt() == null) {
                s.setRevokedAt(Instant.now());
                s.setRevokedReason("LOGOUT");
            }
        });
    }

    @Transactional
    public void revokeAll(Long userId, String reason) {
        repo.revokeAllForUser(userId, Instant.now(), reason);
    }

    @Transactional
    public void revoke(Long userId, Long sessionId, String reason) {
        UserSession s = repo.findByIdAndUserId(sessionId, userId).orElseThrow(() -> ApiException.notFound("Session not found"));
        if (s.getRevokedAt() == null) {
            s.setRevokedAt(Instant.now());
            s.setRevokedReason(reason);
        }
    }

    @Transactional(readOnly = true)
    public List<UserSession> active(Long userId) {
        return repo.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(userId, Instant.now());
    }

    private static String trim(String v, int max) {
        return v == null ? null : (v.length() > max ? v.substring(0, max) : v);
    }
}
