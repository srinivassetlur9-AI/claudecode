package com.lms.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "user_sessions")
@Getter @Setter
public class UserSession extends BaseEntity {
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "refresh_token_hash", nullable = false) private String refreshTokenHash;
    @Column(name = "device_name") private String deviceName;
    @Column(name = "user_agent") private String userAgent;
    @Column(name = "ip_address") private String ipAddress;
    @Column(name = "last_seen_at", nullable = false) private Instant lastSeenAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "revoked_reason") private String revokedReason;
    @Column(name = "device_id") private Long deviceId;

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
