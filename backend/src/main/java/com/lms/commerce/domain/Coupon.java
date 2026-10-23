package com.lms.commerce.domain;

import com.lms.identity.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "coupons")
@Getter @Setter
public class Coupon extends BaseEntity {
    public static final String PERCENT = "PERCENT", FLAT = "FLAT";

    @Column(nullable = false) private String code;
    @Column(name = "discount_type", nullable = false) private String discountType;
    @Column(name = "discount_value", nullable = false) private long discountValue;
    @Column(name = "applies_to", nullable = false) private String appliesTo = "ALL";
    @Column(name = "max_redemptions") private Integer maxRedemptions;
    @Column(name = "redeemed_count", nullable = false) private int redeemedCount;
    @Column(name = "valid_from") private Instant validFrom;
    @Column(name = "valid_until") private Instant validUntil;
    @Column(nullable = false) private boolean active = true;
}
