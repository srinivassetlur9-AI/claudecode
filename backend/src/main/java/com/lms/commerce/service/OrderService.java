package com.lms.commerce.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.commerce.domain.Order;
import com.lms.commerce.domain.Plan;
import com.lms.commerce.domain.PurchasableCourse;
import com.lms.commerce.domain.Refund;
import com.lms.commerce.payment.PaymentGateway;
import com.lms.commerce.repo.*;
import com.lms.commerce.web.CommerceDtos.*;
import com.lms.identity.config.LmsProperties;
import com.lms.identity.domain.User;
import com.lms.identity.repo.UserRepository;
import com.lms.identity.security.Hashing;
import com.lms.identity.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Course and plan purchases. The server decides every price (the browser only names an item and a coupon),
 * the gateway confirms the payment, and fulfilment (invoice, coupon redemption, entitlements) runs exactly once
 * whether the browser callback or the webhook arrives first.
 */
@Service
public class OrderService {
    private record Item(String type, String ref, String title, long price, String currency, Plan plan) {}

    private final OrderRepository orders;
    private final PlanRepository plans;
    private final PurchasableCourseRepository courses;
    private final UserRepository users;
    private final InvoiceRepository invoiceRepo;
    private final RefundRepository refunds;
    private final PaymentGateway gateway;
    private final CouponService coupons;
    private final SubscriptionService subscriptions;
    private final EntitlementService entitlements;
    private final InvoiceService invoices;
    private final LmsProperties props;
    private final ObjectMapper json;

    public OrderService(OrderRepository orders, PlanRepository plans, PurchasableCourseRepository courses,
                        UserRepository users, InvoiceRepository invoiceRepo, RefundRepository refunds,
                        PaymentGateway gateway, CouponService coupons, SubscriptionService subscriptions,
                        EntitlementService entitlements, InvoiceService invoices, LmsProperties props, ObjectMapper json) {
        this.orders = orders;
        this.plans = plans;
        this.courses = courses;
        this.users = users;
        this.invoiceRepo = invoiceRepo;
        this.refunds = refunds;
        this.gateway = gateway;
        this.coupons = coupons;
        this.subscriptions = subscriptions;
        this.entitlements = entitlements;
        this.invoices = invoices;
        this.props = props;
        this.json = json;
    }

    @Transactional
    public CheckoutView create(Long userId, CreateOrderRequest req) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        Item item = resolve(userId, req.itemType(), req.itemRef());

        long discount = 0;
        String couponCode = null;
        if (req.couponCode() != null && !req.couponCode().isBlank()) {
            CouponService.Quote q = coupons.quote(userId, req.couponCode(), item.type(), item.price());
            discount = q.discountPaise();
            couponCode = q.coupon().getCode();
        }

        Order o = new Order();
        o.setOrderNo("ORD-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + Hashing.sha256(Hashing.randomToken(16)).substring(0, 6).toUpperCase());
        o.setUserId(userId);
        o.setItemType(item.type());
        o.setItemRef(item.ref());
        o.setTitle(item.title());
        o.setSubtotalPaise(item.price());
        o.setDiscountPaise(discount);
        o.setTotalPaise(item.price() - discount);
        o.setCurrency(item.currency());
        o.setCouponCode(couponCode);
        orders.save(o);

        if (o.getTotalPaise() == 0) { // fully discounted: nothing to charge
            fulfil(o, null);
            return new CheckoutView(view(o), false, null, null, 0, o.getCurrency(), "IntelliLearn AI", o.getTitle(), null, null);
        }
        // Throws PAYMENTS_NOT_CONFIGURED / PAYMENT_GATEWAY_ERROR, which rolls the order back: no orphan orders.
        o.setRazorpayOrderId(gateway.createOrder(o.getTotalPaise(), o.getCurrency(), o.getOrderNo()));
        return new CheckoutView(view(o), true, gateway.keyId(), o.getRazorpayOrderId(), o.getTotalPaise(), o.getCurrency(),
                "IntelliLearn AI", o.getTitle(), user.getFirstName() + " " + user.getLastName(), user.getEmail());
    }

    /** Browser callback after checkout. The signature proves the gateway captured this payment for this order. */
    @Transactional
    public OrderView confirm(Long userId, Long orderId, ConfirmPaymentRequest req) {
        Order o = orders.lockById(orderId).filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (Order.PAID.equals(o.getStatus())) return view(o); // webhook got there first
        if (!Order.CREATED.equals(o.getStatus())) throw ApiException.conflict("ORDER_NOT_PAYABLE", "This order can no longer be paid");
        if (!gateway.verifyPaymentSignature(o.getRazorpayOrderId(), req.razorpayPaymentId(), req.razorpaySignature())) {
            throw ApiException.badRequest("PAYMENT_VERIFICATION_FAILED", "The payment could not be verified");
        }
        fulfil(o, req.razorpayPaymentId());
        return view(o);
    }

    /** Server-to-server confirmation, so a learner who closes the tab after paying still gets access. */
    @Transactional
    public void handleWebhook(String rawBody, String signature) {
        if (!gateway.verifyWebhookSignature(rawBody, signature)) {
            throw ApiException.badRequest("INVALID_SIGNATURE", "Webhook signature check failed");
        }
        JsonNode root;
        try {
            root = json.readTree(rawBody);
        } catch (Exception e) {
            throw ApiException.badRequest("MALFORMED_REQUEST", "Malformed webhook body");
        }
        String event = root.path("event").asText();
        if (!Set.of("payment.captured", "order.paid").contains(event)) return; // other events are acknowledged and ignored
        JsonNode pay = root.at("/payload/payment/entity");
        String gatewayOrderId = pay.path("order_id").asText(null);
        String paymentId = pay.path("id").asText(null);
        if (gatewayOrderId == null || paymentId == null) return;
        orders.findByRazorpayOrderId(gatewayOrderId)
                .flatMap(o -> orders.lockById(o.getId()))
                .filter(o -> Order.CREATED.equals(o.getStatus()))
                .ifPresent(o -> fulfil(o, paymentId));
    }

    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public CouponQuoteView quote(Long userId, CouponQuoteRequest req) {
        Item item = resolve(userId, req.itemType(), req.itemRef());
        try {
            CouponService.Quote q = coupons.quote(userId, req.code(), item.type(), item.price());
            return new CouponQuoteView(true, q.coupon().getCode(), q.discountPaise(), item.price() - q.discountPaise(),
                    "Coupon applied");
        } catch (ApiException e) {
            if (!"COUPON_INVALID".equals(e.code())) throw e;
            return new CouponQuoteView(false, req.code(), 0, item.price(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<OrderView> mine(Long userId) {
        return orders.findByUserIdOrderByIdDesc(userId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public OrderView get(Long userId, Long id) {
        return view(orders.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Order not found")));
    }

    @Transactional(readOnly = true)
    public List<OrderView> recent() {
        return orders.findTop100ByOrderByIdDesc().stream().map(this::view).toList();
    }

    // ---- internals ----

    private Item resolve(Long userId, String rawType, String ref) {
        String type = rawType.trim().toUpperCase();
        if (Order.PLAN.equals(type)) {
            Plan p = plans.findByCode(ref.trim().toUpperCase()).filter(Plan::isActive)
                    .orElseThrow(() -> ApiException.notFound("Plan not found"));
            if ("FREE".equals(p.getPlanType())) throw ApiException.badRequest("FREE_PLAN", "The Free plan is included for everyone");
            if (!p.isSelfServe()) throw ApiException.badRequest("CONTACT_SALES", "This plan is arranged with our sales team");
            return new Item(type, p.getCode(), p.getName() + " plan", p.getPricePaise(), p.getCurrency(), p);
        }
        if (Order.COURSE.equals(type)) {
            PurchasableCourse c = courses.findByCourseRef(ref.trim()).filter(PurchasableCourse::isActive)
                    .orElseThrow(() -> ApiException.notFound("Course not found"));
            if (entitlements.owns(userId, EntitlementService.COURSE, c.getCourseRef())) {
                throw ApiException.conflict("ALREADY_OWNED", "You already own this course");
            }
            return new Item(type, c.getCourseRef(), c.getTitle(), c.getPricePaise(), c.getCurrency(), null);
        }
        throw ApiException.badRequest("VALIDATION_FAILED", "itemType must be PLAN or COURSE");
    }

    /** Idempotent: runs once, under the row lock taken by the caller. */
    private void fulfil(Order o, String paymentId) {
        if (Order.PAID.equals(o.getStatus())) return;
        o.setStatus(Order.PAID);
        o.setPaidAt(Instant.now());
        o.setRazorpayPaymentId(paymentId);
        User user = users.findById(o.getUserId()).orElseThrow();
        if (o.getCouponCode() != null) coupons.redeem(o.getUserId(), o.getCouponCode(), o.getId());
        invoices.issue(o, user);
        String source = "ORDER:" + o.getOrderNo();
        if (Order.PLAN.equals(o.getItemType())) {
            subscriptions.activate(o.getUserId(), plans.findByCode(o.getItemRef()).orElseThrow(), o.getId(), source);
        } else {
            entitlements.grant(o.getUserId(), EntitlementService.COURSE, o.getItemRef(), source, null);
        }
    }

    OrderView view(Order o) {
        Long invoiceId = invoiceRepo.findByOrderId(o.getId()).map(i -> i.getId()).orElse(null);
        Refund latest = refunds.findFirstByOrderIdOrderByIdDesc(o.getId()).orElse(null);
        boolean refundable = Order.PAID.equals(o.getStatus()) && o.getTotalPaise() > 0 && o.getPaidAt() != null
                && o.getPaidAt().plus(Duration.ofDays(props.commerce().refundWindowDays())).isAfter(Instant.now())
                && !refunds.existsByOrderIdAndStatusIn(o.getId(), List.of(Refund.REQUESTED, Refund.PROCESSED));
        return new OrderView(o.getId(), o.getOrderNo(), o.getUserId(), o.getItemType(), o.getItemRef(), o.getTitle(),
                o.getSubtotalPaise(), o.getDiscountPaise(), o.getTotalPaise(), o.getCurrency(), o.getCouponCode(),
                o.getStatus(), o.getPaidAt(), o.getCreatedAt(), invoiceId, refundable,
                latest == null ? null : latest.getStatus());
    }
}
