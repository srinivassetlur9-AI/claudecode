package com.lms.commerce.service;

import com.lms.commerce.domain.Order;
import com.lms.commerce.domain.Refund;
import com.lms.commerce.payment.PaymentGateway;
import com.lms.commerce.repo.OrderRepository;
import com.lms.commerce.repo.RefundRepository;
import com.lms.commerce.web.CommerceDtos.RefundView;
import com.lms.identity.config.LmsProperties;
import com.lms.identity.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Learners request a refund inside the refund window; a finance admin approves (money goes back through the gateway) or rejects. */
@Service
public class RefundService {
    private final RefundRepository refunds;
    private final OrderRepository orders;
    private final PaymentGateway gateway;
    private final EntitlementService entitlements;
    private final SubscriptionService subscriptions;
    private final LmsProperties props;

    public RefundService(RefundRepository refunds, OrderRepository orders, PaymentGateway gateway,
                         EntitlementService entitlements, SubscriptionService subscriptions, LmsProperties props) {
        this.refunds = refunds;
        this.orders = orders;
        this.gateway = gateway;
        this.entitlements = entitlements;
        this.subscriptions = subscriptions;
        this.props = props;
    }

    @Transactional
    public RefundView request(Long userId, Long orderId, String reason) {
        Order o = orders.findByIdAndUserId(orderId, userId).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!Order.PAID.equals(o.getStatus()) || o.getTotalPaise() == 0) {
            throw ApiException.badRequest("NOT_REFUNDABLE", "Only paid orders can be refunded");
        }
        if (o.getPaidAt().plus(Duration.ofDays(props.commerce().refundWindowDays())).isBefore(Instant.now())) {
            throw ApiException.badRequest("REFUND_WINDOW_CLOSED",
                    "Refunds are only possible within " + props.commerce().refundWindowDays() + " days of payment");
        }
        if (refunds.existsByOrderIdAndStatusIn(orderId, List.of(Refund.REQUESTED, Refund.PROCESSED))) {
            throw ApiException.conflict("REFUND_EXISTS", "A refund for this order is already in progress");
        }
        Refund r = new Refund();
        r.setOrderId(orderId);
        r.setUserId(userId);
        r.setAmountPaise(o.getTotalPaise());
        r.setReason(reason.trim());
        return view(refunds.save(r), o);
    }

    @Transactional
    public RefundView approve(Long adminId, Long refundId, String note) {
        Refund r = refunds.lockById(refundId).orElseThrow(() -> ApiException.notFound("Refund not found"));
        if (!Refund.REQUESTED.equals(r.getStatus())) throw ApiException.conflict("REFUND_DECIDED", "This refund was already decided");
        Order o = orders.lockById(r.getOrderId()).orElseThrow();
        if (!Order.PAID.equals(o.getStatus()) || o.getRazorpayPaymentId() == null) {
            throw ApiException.conflict("NOT_REFUNDABLE", "This order has no captured payment to refund");
        }
        // Money moves first: if the gateway refuses, this throws and nothing below is committed, so the admin can retry.
        r.setRazorpayRefundId(gateway.refund(o.getRazorpayPaymentId(), r.getAmountPaise(), r.getReason()));
        r.setStatus(Refund.PROCESSED);
        r.setDecidedBy(adminId);
        r.setDecidedAt(Instant.now());
        r.setDecisionNote(note);
        o.setStatus(Order.REFUNDED);
        String source = "ORDER:" + o.getOrderNo();
        entitlements.revokeBySource(o.getUserId(), source); // takes back exactly what this order granted
        subscriptions.refundOrder(o.getId());
        return view(r, o);
    }

    @Transactional
    public RefundView reject(Long adminId, Long refundId, String note) {
        Refund r = refunds.lockById(refundId).orElseThrow(() -> ApiException.notFound("Refund not found"));
        if (!Refund.REQUESTED.equals(r.getStatus())) throw ApiException.conflict("REFUND_DECIDED", "This refund was already decided");
        r.setStatus(Refund.REJECTED);
        r.setDecidedBy(adminId);
        r.setDecidedAt(Instant.now());
        r.setDecisionNote(note);
        return view(r, orders.findById(r.getOrderId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<RefundView> mine(Long userId) {
        return refunds.findByUserIdOrderByIdDesc(userId).stream()
                .map(r -> view(r, orders.findById(r.getOrderId()).orElseThrow())).toList();
    }

    @Transactional(readOnly = true)
    public List<RefundView> all() {
        return refunds.findTop200ByOrderByIdDesc().stream()
                .map(r -> view(r, orders.findById(r.getOrderId()).orElseThrow())).toList();
    }

    private static RefundView view(Refund r, Order o) {
        return new RefundView(r.getId(), r.getOrderId(), o.getOrderNo(), r.getUserId(), r.getAmountPaise(), r.getReason(),
                r.getStatus(), r.getDecisionNote(), r.getCreatedAt(), r.getDecidedAt());
    }
}
