package com.lms.commerce.domain;

import com.lms.identity.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "subscriptions")
@Getter @Setter
public class Subscription extends BaseEntity {
    public static final String ACTIVE = "ACTIVE", CANCELLED = "CANCELLED", EXPIRED = "EXPIRED", REFUNDED = "REFUNDED";

    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "plan_id", nullable = false) private Long planId;
    @Column(name = "order_id") private Long orderId;
    @Column(nullable = false) private String status = ACTIVE;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at") private Instant endsAt;
}
