package com.lms.commerce.web;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class CommerceDtos {
    private CommerceDtos() {}

    // ---- catalogue ----
    public record PlanView(Long id, String code, String name, String planType, long pricePaise, String currency,
                           Integer durationDays, boolean selfServe, String description, List<String> features,
                           boolean active) {}
    public record CourseView(String courseRef, String title, long pricePaise, String currency) {}
    public record UpdatePlanRequest(@Size(max = 100) String name, @PositiveOrZero Long pricePaise, Boolean active) {}

    // ---- subscription ----
    public record SubscriptionView(String planCode, String planName, String planType, String status,
                                   Instant startsAt, Instant endsAt, boolean free, Long daysLeft) {}
    public record AccessCheck(boolean allowed) {}

    // ---- orders ----
    public record CreateOrderRequest(@NotBlank String itemType, @NotBlank String itemRef, @Size(max = 40) String couponCode) {}
    public record ConfirmPaymentRequest(@NotBlank String razorpayPaymentId, @NotBlank String razorpaySignature) {}
    public record OrderView(Long id, String orderNo, Long userId, String itemType, String itemRef, String title,
                            long subtotalPaise, long discountPaise, long totalPaise, String currency, String couponCode,
                            String status, Instant paidAt, Instant createdAt, Long invoiceId, boolean refundable,
                            String refundStatus) {}
    /** Everything the browser needs to open Razorpay Checkout. paymentRequired=false means the order was free and is already paid. */
    public record CheckoutView(OrderView order, boolean paymentRequired, String keyId, String razorpayOrderId,
                               long amount, String currency, String name, String description,
                               String prefillName, String prefillEmail) {}

    // ---- coupons ----
    public record CouponQuoteRequest(@NotBlank @Size(max = 40) String code, @NotBlank String itemType, @NotBlank String itemRef) {}
    public record CouponQuoteView(boolean valid, String code, long discountPaise, long totalPaise, String message) {}
    public record CouponView(Long id, String code, String discountType, long discountValue, String appliesTo,
                             Integer maxRedemptions, int redeemedCount, Instant validFrom, Instant validUntil, boolean active) {}
    public record CreateCouponRequest(@NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String code,
                                      @NotBlank @Pattern(regexp = "PERCENT|FLAT") String discountType,
                                      @Positive long discountValue,
                                      @Pattern(regexp = "ALL|PLAN|COURSE") String appliesTo,
                                      @Positive Integer maxRedemptions, Instant validUntil) {}
    public record MyCouponView(String code, String discountType, long discountValue, Long orderId, Instant usedAt) {}

    // ---- invoices & refunds ----
    public record InvoiceView(Long id, String invoiceNo, Long orderId, String orderNo, long amountPaise, String currency,
                              String billingName, String billingEmail, String description, Instant issuedAt) {}
    public record RefundRequest(@NotBlank @Size(max = 500) String reason) {}
    public record DecisionRequest(@Size(max = 500) String note) {}
    public record RefundView(Long id, Long orderId, String orderNo, Long userId, long amountPaise, String reason,
                             String status, String decisionNote, Instant createdAt, Instant decidedAt) {}

    // ---- devices ----
    public record DeviceView(Long id, String name, String platform, String status, Instant registeredAt,
                             Instant lastSeenAt, String lastIp, Instant deactivatedAt, String deactivationReason,
                             boolean current) {}
    public record DevicesView(int maxDevices, long activeCount, List<DeviceView> devices) {}
    public record DeviceEventView(Long id, Long userId, Long deviceId, String eventType, String detail, String ipAddress, Instant createdAt) {}
    public record DeviceActionRequest(@Size(max = 200) String reason) {}
    /** Result of the device/license validation API that apps call before serving protected content. */
    public record LicenseView(boolean valid, String reason, Long deviceId, long activeDevices, int maxDevices,
                              String planCode, int activeEntitlements) {}
}
