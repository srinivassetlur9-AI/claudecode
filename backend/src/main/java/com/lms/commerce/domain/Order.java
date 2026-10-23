package com.lms.commerce.domain;

import com.lms.identity.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "orders")
@Getter @Setter
public class Order extends BaseEntity {
    public static final String PLAN = "PLAN", COURSE = "COURSE";
    public static final String CREATED = "CREATED", PAID = "PAID", FAILED = "FAILED", REFUNDED = "REFUNDED";

    @Column(name = "order_no", nullable = false) private String orderNo;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "item_type", nullable = false) private String itemType;
    @Column(name = "item_ref", nullable = false) private String itemRef;
    @Column(nullable = false) private String title;
    @Column(name = "subtotal_paise", nullable = false) private long subtotalPaise;
    @Column(name = "discount_paise", nullable = false) private long discountPaise;
    @Column(name = "total_paise", nullable = false) private long totalPaise;
    @Column(nullable = false) private String currency;
    @Column(name = "coupon_code") private String couponCode;
    @Column(nullable = false) private String status = CREATED;
    @Column(name = "razorpay_order_id") private String razorpayOrderId;
    @Column(name = "razorpay_payment_id") private String razorpayPaymentId;
    @Column(name = "paid_at") private Instant paidAt;
}
