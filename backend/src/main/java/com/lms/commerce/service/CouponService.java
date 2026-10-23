package com.lms.commerce.service;

import com.lms.commerce.domain.Coupon;
import com.lms.commerce.domain.CouponRedemption;
import com.lms.commerce.repo.CouponRedemptionRepository;
import com.lms.commerce.repo.CouponRepository;
import com.lms.commerce.web.CommerceDtos.*;
import com.lms.identity.web.ApiException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {
    private static final Logger log = LoggerFactory.getLogger(CouponService.class);

    public record Quote(Coupon coupon, long discountPaise) {}

    private final CouponRepository coupons;
    private final CouponRedemptionRepository redemptions;

    public CouponService(CouponRepository coupons, CouponRedemptionRepository redemptions) {
        this.coupons = coupons;
        this.redemptions = redemptions;
    }

    /** Validates a code for this learner and item, or throws COUPON_INVALID with a reason the UI can show. */
    @Transactional(readOnly = true, noRollbackFor = ApiException.class) // callers may catch COUPON_INVALID
    public Quote quote(Long userId, String code, String itemType, long subtotalPaise) {
        Coupon c = coupons.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> invalid("This coupon code does not exist"));
        Instant now = Instant.now();
        if (!c.isActive()) throw invalid("This coupon is no longer active");
        if (c.getValidFrom() != null && c.getValidFrom().isAfter(now)) throw invalid("This coupon is not valid yet");
        if (c.getValidUntil() != null && !c.getValidUntil().isAfter(now)) throw invalid("This coupon has expired");
        if (c.getMaxRedemptions() != null && c.getRedeemedCount() >= c.getMaxRedemptions()) {
            throw invalid("This coupon has reached its usage limit");
        }
        if (!"ALL".equals(c.getAppliesTo()) && !c.getAppliesTo().equals(itemType)) {
            throw invalid("This coupon only applies to " + c.getAppliesTo().toLowerCase() + " purchases");
        }
        if (redemptions.existsByCouponIdAndUserId(c.getId(), userId)) throw invalid("You have already used this coupon");
        long discount = Coupon.PERCENT.equals(c.getDiscountType())
                ? subtotalPaise * Math.min(c.getDiscountValue(), 100) / 100
                : c.getDiscountValue();
        return new Quote(c, Math.min(discount, subtotalPaise));
    }

    /** Called once the order is paid. The cap is claimed atomically; a lost race is logged, never undone, because the money is already taken. */
    @Transactional
    public void redeem(Long userId, String code, Long orderId) {
        coupons.findByCodeIgnoreCase(code).ifPresent(c -> {
            if (redemptions.existsByCouponIdAndUserId(c.getId(), userId)) return;
            if (coupons.claim(c.getId()) == 0) log.warn("Coupon {} exceeded its cap by a concurrent redemption", c.getCode());
            CouponRedemption r = new CouponRedemption();
            r.setCouponId(c.getId());
            r.setUserId(userId);
            r.setOrderId(orderId);
            redemptions.save(r);
        });
    }

    @Transactional(readOnly = true)
    public List<MyCouponView> mine(Long userId) {
        return redemptions.findByUserIdOrderByIdDesc(userId).stream().map(r -> {
            Coupon c = coupons.findById(r.getCouponId()).orElse(null);
            return new MyCouponView(c == null ? "?" : c.getCode(), c == null ? null : c.getDiscountType(),
                    c == null ? 0 : c.getDiscountValue(), r.getOrderId(), r.getCreatedAt());
        }).toList();
    }

    // ---- admin ----

    @Transactional(readOnly = true)
    public List<CouponView> all() {
        return coupons.findAllByOrderByIdDesc().stream().map(CouponService::view).toList();
    }

    @Transactional
    public CouponView create(CreateCouponRequest req) {
        String code = req.code().trim().toUpperCase();
        if (coupons.findByCodeIgnoreCase(code).isPresent()) throw ApiException.conflict("COUPON_EXISTS", "A coupon with this code already exists");
        if (Coupon.PERCENT.equals(req.discountType()) && req.discountValue() > 100) {
            throw ApiException.badRequest("VALIDATION_FAILED", "A percentage discount cannot exceed 100");
        }
        Coupon c = new Coupon();
        c.setCode(code);
        c.setDiscountType(req.discountType());
        c.setDiscountValue(req.discountValue());
        c.setAppliesTo(req.appliesTo() == null ? "ALL" : req.appliesTo());
        c.setMaxRedemptions(req.maxRedemptions());
        c.setValidUntil(req.validUntil());
        return view(coupons.save(c));
    }

    @Transactional
    public CouponView setActive(Long id, boolean active) {
        Coupon c = coupons.findById(id).orElseThrow(() -> ApiException.notFound("Coupon not found"));
        c.setActive(active);
        return view(c);
    }

    private static CouponView view(Coupon c) {
        return new CouponView(c.getId(), c.getCode(), c.getDiscountType(), c.getDiscountValue(), c.getAppliesTo(),
                c.getMaxRedemptions(), c.getRedeemedCount(), c.getValidFrom(), c.getValidUntil(), c.isActive());
    }

    private static ApiException invalid(String msg) {
        return ApiException.badRequest("COUPON_INVALID", msg);
    }
}
