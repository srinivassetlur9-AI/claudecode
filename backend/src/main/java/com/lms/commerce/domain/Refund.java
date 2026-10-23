package com.lms.commerce.domain;

import com.lms.identity.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "refunds")
@Getter @Setter
public class Refund extends BaseEntity {
    public static final String REQUESTED = "REQUESTED", PROCESSED = "PROCESSED", REJECTED = "REJECTED", FAILED = "FAILED";

    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "amount_paise", nullable = false) private long amountPaise;
    @Column(nullable = false) private String reason;
    @Column(nullable = false) private String status = REQUESTED;
    @Column(name = "razorpay_refund_id") private String razorpayRefundId;
    @Column(name = "decided_by") private Long decidedBy;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "decision_note") private String decisionNote;
}
