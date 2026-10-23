package com.lms.commerce.web;

import com.lms.commerce.service.*;
import com.lms.commerce.web.CommerceDtos.*;
import com.lms.identity.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Learner-facing subscription, order, coupon, invoice and refund API. Plans and courses are public (pricing page). */
@RestController
@RequestMapping("/api/v1/commerce")
public class CommerceController {
    private final CatalogService catalog;
    private final SubscriptionService subscriptions;
    private final EntitlementService entitlements;
    private final OrderService orders;
    private final CouponService coupons;
    private final InvoiceService invoices;
    private final RefundService refunds;

    public CommerceController(CatalogService catalog, SubscriptionService subscriptions, EntitlementService entitlements,
                              OrderService orders, CouponService coupons, InvoiceService invoices, RefundService refunds) {
        this.catalog = catalog;
        this.subscriptions = subscriptions;
        this.entitlements = entitlements;
        this.orders = orders;
        this.coupons = coupons;
        this.invoices = invoices;
        this.refunds = refunds;
    }

    // ---- catalogue (public) ----
    @GetMapping("/plans")
    public List<PlanView> plans() { return catalog.activePlans(); }

    @GetMapping("/courses")
    public List<CourseView> courses() { return catalog.courses(); }

    // ---- subscription & entitlements ----
    @GetMapping("/subscription")
    public SubscriptionView subscription(@AuthenticationPrincipal AuthPrincipal me) {
        return subscriptions.current(me.userId());
    }

    /** Entitlement check other modules call: may the caller open this course / feature? */
    @GetMapping("/entitlements/check")
    public AccessCheck check(@AuthenticationPrincipal AuthPrincipal me, @RequestParam String type, @RequestParam String ref) {
        return new AccessCheck(entitlements.hasAccess(me.userId(), type.toUpperCase(), ref));
    }

    // ---- orders & payment ----
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutView createOrder(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody CreateOrderRequest req) {
        return orders.create(me.userId(), req);
    }

    @PostMapping("/orders/{id}/confirm")
    public OrderView confirm(@AuthenticationPrincipal AuthPrincipal me, @PathVariable Long id,
                             @Valid @RequestBody ConfirmPaymentRequest req) {
        return orders.confirm(me.userId(), id, req);
    }

    @GetMapping("/orders")
    public List<OrderView> myOrders(@AuthenticationPrincipal AuthPrincipal me) { return orders.mine(me.userId()); }

    @GetMapping("/orders/{id}")
    public OrderView order(@AuthenticationPrincipal AuthPrincipal me, @PathVariable Long id) { return orders.get(me.userId(), id); }

    /** Razorpay calls this after a captured payment. Authenticated by HMAC signature, not by a bearer token. */
    @PostMapping("/webhooks/razorpay")
    public ResponseEntity<Void> webhook(@RequestBody String body,
                                        @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        orders.handleWebhook(body, signature);
        return ResponseEntity.ok().build();
    }

    // ---- coupons ----
    @PostMapping("/coupons/validate")
    public CouponQuoteView validateCoupon(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody CouponQuoteRequest req) {
        return orders.quote(me.userId(), req);
    }

    @GetMapping("/coupons/mine")
    public List<MyCouponView> myCoupons(@AuthenticationPrincipal AuthPrincipal me) { return coupons.mine(me.userId()); }

    // ---- invoices & refunds ----
    @GetMapping("/invoices")
    public List<InvoiceView> myInvoices(@AuthenticationPrincipal AuthPrincipal me) { return invoices.mine(me.userId()); }

    @GetMapping("/invoices/{id}")
    public InvoiceView invoice(@AuthenticationPrincipal AuthPrincipal me, @PathVariable Long id) { return invoices.get(me.userId(), id); }

    @PostMapping("/orders/{id}/refund")
    @ResponseStatus(HttpStatus.CREATED)
    public RefundView requestRefund(@AuthenticationPrincipal AuthPrincipal me, @PathVariable Long id,
                                    @Valid @RequestBody RefundRequest req) {
        return refunds.request(me.userId(), id, req.reason());
    }

    @GetMapping("/refunds")
    public List<RefundView> myRefunds(@AuthenticationPrincipal AuthPrincipal me) { return refunds.mine(me.userId()); }
}
