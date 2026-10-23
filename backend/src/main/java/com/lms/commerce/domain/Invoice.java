package com.lms.commerce.domain;

import com.lms.identity.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "invoices")
@Getter @Setter
public class Invoice extends BaseEntity {
    @Column(name = "invoice_no", nullable = false) private String invoiceNo;
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "amount_paise", nullable = false) private long amountPaise;
    @Column(nullable = false) private String currency;
    @Column(name = "billing_name", nullable = false) private String billingName;
    @Column(name = "billing_email", nullable = false) private String billingEmail;
    @Column(nullable = false) private String description;
    @Column(name = "issued_at", nullable = false) private Instant issuedAt;
}
