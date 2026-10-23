package com.lms.commerce.domain;

import com.lms.identity.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "coupon_redemptions")
@Getter @Setter
public class CouponRedemption extends BaseEntity {
    @Column(name = "coupon_id", nullable = false) private Long couponId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "order_id", nullable = false) private Long orderId;
}
