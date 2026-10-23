package com.lms.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Hook for Module 2. Module 1 never writes to this table. */
@Entity @Table(name = "user_entitlements")
@Getter @Setter
public class UserEntitlement extends BaseEntity {
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "entitlement_type", nullable = false) private String entitlementType;
    @Column(name = "resource_ref", nullable = false) private String resourceRef;
    private String source;
    @Column(name = "valid_from") private Instant validFrom;
    @Column(name = "valid_until") private Instant validUntil;
}
