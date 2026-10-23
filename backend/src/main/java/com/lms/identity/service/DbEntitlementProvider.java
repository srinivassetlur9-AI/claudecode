package com.lms.identity.service;

import com.lms.identity.repo.UserEntitlementRepository;
import com.lms.identity.web.Dtos.EntitlementView;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DbEntitlementProvider implements EntitlementProvider {
    private final UserEntitlementRepository repo;

    public DbEntitlementProvider(UserEntitlementRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EntitlementView> entitlementsFor(Long userId) {
        Instant now = Instant.now();
        return repo.findByUserId(userId).stream()
                .filter(e -> e.getValidUntil() == null || e.getValidUntil().isAfter(now))
                .map(e -> new EntitlementView(e.getEntitlementType(), e.getResourceRef(), e.getSource(), e.getValidUntil()))
                .toList();
    }
}
