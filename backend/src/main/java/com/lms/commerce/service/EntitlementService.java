package com.lms.commerce.service;

import com.lms.identity.domain.UserEntitlement;
import com.lms.identity.repo.UserEntitlementRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entitlement service: the single place that grants, revokes and answers "may this user open this?".
 * It writes the {@code user_entitlements} table that Module 1's {@code EntitlementProvider} reads, so every module
 * sees the same answer. Each grant carries a source ({@code ORDER:<orderNo>}) so a refund can take back exactly
 * what that order gave.
 */
@Service
public class EntitlementService {
    public static final String COURSE = "COURSE", PLAN = "PLAN", FEATURE = "FEATURE";
    public static final String ALL_COURSES = "ALL_COURSES";

    private final UserEntitlementRepository repo;

    public EntitlementService(UserEntitlementRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void grant(Long userId, String type, String ref, String source, Instant validUntil) {
        UserEntitlement e = new UserEntitlement();
        e.setUserId(userId);
        e.setEntitlementType(type);
        e.setResourceRef(ref);
        e.setSource(source);
        e.setValidFrom(Instant.now());
        e.setValidUntil(validUntil);
        repo.save(e);
    }

    @Transactional
    public void revokeBySource(Long userId, String source) {
        repo.deleteAll(repo.findByUserIdAndSource(userId, source));
    }

    /** Course access = bought it directly, or holds a live plan that includes the whole library. */
    @Transactional(readOnly = true)
    public boolean hasAccess(Long userId, String type, String ref) {
        Instant now = Instant.now();
        return repo.findByUserId(userId).stream()
                .filter(e -> e.getValidUntil() == null || e.getValidUntil().isAfter(now))
                .anyMatch(e -> (e.getEntitlementType().equals(type) && e.getResourceRef().equals(ref))
                        || (COURSE.equals(type) && FEATURE.equals(e.getEntitlementType())
                            && ALL_COURSES.equals(e.getResourceRef())));
    }

    /** Direct ownership only (ignores plans); used to stop buying a course twice. */
    @Transactional(readOnly = true)
    public boolean owns(Long userId, String type, String ref) {
        Instant now = Instant.now();
        return repo.findByUserId(userId).stream()
                .filter(e -> e.getValidUntil() == null || e.getValidUntil().isAfter(now))
                .anyMatch(e -> e.getEntitlementType().equals(type) && e.getResourceRef().equals(ref));
    }

    @Transactional(readOnly = true)
    public int activeCount(Long userId) {
        Instant now = Instant.now();
        return (int) repo.findByUserId(userId).stream()
                .filter(e -> e.getValidUntil() == null || e.getValidUntil().isAfter(now)).count();
    }
}
