package com.lms.commerce.web;

import com.lms.commerce.service.*;
import com.lms.commerce.web.CommerceDtos.*;
import com.lms.identity.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Finance and support tools. Every call is checked through the shared permission contract. */
@RestController
@RequestMapping("/api/v1/commerce/admin")
public class CommerceAdminController {
    private final AccessGuard guard;
    private final CatalogService catalog;
    private final CouponService coupons;
    private final OrderService orders;
    private final RefundService refunds;
    private final DeviceService devices;

    public CommerceAdminController(AccessGuard guard, CatalogService catalog, CouponService coupons, OrderService orders,
                                   RefundService refunds, DeviceService devices) {
        this.guard = guard;
        this.catalog = catalog;
        this.coupons = coupons;
        this.orders = orders;
        this.refunds = refunds;
        this.devices = devices;
    }

    // ---- finance:manage ----
    @GetMapping("/plans")
    public List<PlanView> plans(@AuthenticationPrincipal AuthPrincipal me) {
        guard.require(me, "finance:manage");
        return catalog.allPlans();
    }

    @PutMapping("/plans/{id}")
    public PlanView updatePlan(@AuthenticationPrincipal AuthPrincipal me, @PathVariable Long id, @Valid @RequestBody UpdatePlanRequest req) {
        guard.require(me, "finance:manage");
        return catalog.update(id, req);
    }

    @GetMapping("/coupons")
    public List<CouponView> coupons(@AuthenticationPrincipal AuthPrincipal me) {
        guard.require(me, "finance:manage");
        return coupons.all();
    }

    @PostMapping("/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    public CouponView createCoupon(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody CreateCouponRequest req) {
        guard.require(me, "finance:manage");
        return coupons.create(req);
    }

    @PutMapping("/coupons/{id}/active")
    public CouponView setCouponActive(@AuthenticationPrincipal AuthPrincipal me, @PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        guard.require(me, "finance:manage");
        return coupons.setActive(id, Boolean.TRUE.equals(body.get("active")));
    }

    @GetMapping("/orders")
    public List<OrderView> orders(@AuthenticationPrincipal AuthPrincipal me) {
        guard.require(me, "finance:manage");
        return orders.recent();
    }

    @GetMapping("/refunds")
    public List<RefundView> refunds(@AuthenticationPrincipal AuthPrincipal me) {
        guard.require(me, "finance:manage");
        return refunds.all();
    }

    @PostMapping("/refunds/{id}/approve")
    public RefundView approve(@AuthenticationPrincipal AuthPrincipal me, @PathVariable Long id, @RequestBody(required = false) DecisionRequest req) {
        guard.require(me, "finance:manage");
        return refunds.approve(me.userId(), id, req == null ? null : req.note());
    }

    @PostMapping("/refunds/{id}/reject")
    public RefundView reject(@AuthenticationPrincipal AuthPrincipal me, @PathVariable Long id, @RequestBody(required = false) DecisionRequest req) {
        guard.require(me, "finance:manage");
        return refunds.reject(me.userId(), id, req == null ? null : req.note());
    }

    // ---- device audit: finance / support ----
    @GetMapping("/users/{userId}/devices")
    public DevicesView userDevices(@AuthenticationPrincipal AuthPrincipal me, @PathVariable Long userId) {
        guard.require(me, "device:read");
        return devices.adminList(userId);
    }

    @GetMapping("/devices/events")
    public List<DeviceEventView> events(@AuthenticationPrincipal AuthPrincipal me, @RequestParam(required = false) Long userId) {
        guard.require(me, "device:read");
        return devices.adminEvents(userId);
    }

    @PostMapping("/users/{userId}/devices/reset")
    public Map<String, Integer> reset(@AuthenticationPrincipal AuthPrincipal me, @PathVariable Long userId) {
        guard.require(me, "device:manage");
        return Map.of("deactivated", devices.adminReset(me.userId(), userId));
    }
}
