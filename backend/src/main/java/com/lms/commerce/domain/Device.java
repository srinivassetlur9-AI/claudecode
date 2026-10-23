package com.lms.commerce.domain;

import com.lms.identity.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "devices")
@Getter @Setter
public class Device extends BaseEntity {
    public static final String ACTIVE = "ACTIVE", DEACTIVATED = "DEACTIVATED", REPLACED = "REPLACED";

    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "device_key_hash", nullable = false) private String deviceKeyHash;
    @Column(nullable = false) private String name;
    private String platform;
    @Column(nullable = false) private String status = ACTIVE;
    @Column(name = "registered_at", nullable = false) private Instant registeredAt;
    @Column(name = "last_seen_at", nullable = false) private Instant lastSeenAt;
    @Column(name = "last_ip") private String lastIp;
    @Column(name = "deactivated_at") private Instant deactivatedAt;
    @Column(name = "deactivation_reason") private String deactivationReason;
    @Column(name = "replaced_by") private Long replacedBy;

    public boolean isActive() { return ACTIVE.equals(status); }
}
