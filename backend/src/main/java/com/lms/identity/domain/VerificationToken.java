package com.lms.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "verification_tokens")
@Getter @Setter
public class VerificationToken extends BaseEntity {
    @Column(name = "user_id", nullable = false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TokenPurpose purpose;
    @Column(name = "token_hash", nullable = false) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "used_at") private Instant usedAt;
    @Column(nullable = false) private int attempts;
}
