package com.lms.commerce.service;

import com.lms.commerce.domain.Plan;
import com.lms.commerce.domain.Subscription;
import com.lms.commerce.repo.PlanRepository;
import com.lms.commerce.repo.SubscriptionRepository;
import com.lms.commerce.web.CommerceDtos.SubscriptionView;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plans are fixed-term purchases: buying one starts (or, if the same plan is already running, extends) a
 * subscription and grants the matching entitlements. Everyone is on the Free plan unless a paid one is running.
 */
@Service
public class SubscriptionService {
    private final SubscriptionRepository subs;
    private final PlanRepository plans;
    private final EntitlementService entitlements;

    public SubscriptionService(SubscriptionRepository subs, PlanRepository plans, EntitlementService entitlements) {
        this.subs = subs;
        this.plans = plans;
        this.entitlements = entitlements;
    }

    @Transactional
    public void activate(Long userId, Plan plan, Long orderId, String source) {
        Instant now = Instant.now();
        Instant start = subs.findByUserIdOrderByIdDesc(userId).stream()
                .filter(s -> s.getPlanId().equals(plan.getId()) && Subscription.ACTIVE.equals(s.getStatus())
                        && s.getEndsAt() != null && s.getEndsAt().isAfter(now))
                .map(Subscription::getEndsAt).max(Comparator.naturalOrder()).orElse(now); // renewing early extends
        Instant end = plan.getDurationDays() == null ? null : start.plus(Duration.ofDays(plan.getDurationDays()));

        Subscription s = new Subscription();
        s.setUserId(userId);
        s.setPlanId(plan.getId());
        s.setOrderId(orderId);
        s.setStartsAt(start);
        s.setEndsAt(end);
        subs.save(s);

        entitlements.grant(userId, EntitlementService.PLAN, plan.getCode(), source, end);
        if (plan.isGrantsAllCourses()) {
            entitlements.grant(userId, EntitlementService.FEATURE, EntitlementService.ALL_COURSES, source, end);
        }
    }

    /** Marks the subscriptions an order created as refunded; the caller revokes the entitlements. */
    @Transactional
    public void refundOrder(Long orderId) {
        subs.findByOrderId(orderId).forEach(s -> s.setStatus(Subscription.REFUNDED));
    }

    /** The plan the learner is on right now: the live paid plan that runs longest, else Free. */
    @Transactional(readOnly = true)
    public SubscriptionView current(Long userId) {
        Instant now = Instant.now();
        Optional<Subscription> live = subs.findByUserIdOrderByIdDesc(userId).stream()
                .filter(s -> Subscription.ACTIVE.equals(s.getStatus()) && !s.getStartsAt().isAfter(now)
                        && (s.getEndsAt() == null || s.getEndsAt().isAfter(now)))
                .max(Comparator.comparing(s -> s.getEndsAt() == null ? Instant.MAX : s.getEndsAt()));
        if (live.isPresent()) {
            Subscription s = live.get();
            Plan p = plans.findById(s.getPlanId()).orElseThrow();
            Long daysLeft = s.getEndsAt() == null ? null : Math.max(0, Duration.between(now, s.getEndsAt()).toDays());
            return new SubscriptionView(p.getCode(), p.getName(), p.getPlanType(), s.getStatus(), s.getStartsAt(),
                    s.getEndsAt(), false, daysLeft);
        }
        Plan free = plans.findByCode("FREE").orElseThrow();
        return new SubscriptionView(free.getCode(), free.getName(), free.getPlanType(), Subscription.ACTIVE, null, null, true, null);
    }

    @Transactional(readOnly = true)
    public List<Subscription> history(Long userId) {
        return subs.findByUserIdOrderByIdDesc(userId);
    }
}
