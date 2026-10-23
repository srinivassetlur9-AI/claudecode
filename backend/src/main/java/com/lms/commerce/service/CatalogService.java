package com.lms.commerce.service;

import com.lms.commerce.domain.Plan;
import com.lms.commerce.repo.PlanRepository;
import com.lms.commerce.repo.PurchasableCourseRepository;
import com.lms.commerce.web.CommerceDtos.*;
import com.lms.identity.web.ApiException;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private final PlanRepository plans;
    private final PurchasableCourseRepository courses;

    public CatalogService(PlanRepository plans, PurchasableCourseRepository courses) {
        this.plans = plans;
        this.courses = courses;
    }

    @Transactional(readOnly = true)
    public List<PlanView> activePlans() {
        return plans.findByActiveTrueOrderBySortOrderAsc().stream().map(CatalogService::view).toList();
    }

    @Transactional(readOnly = true)
    public List<PlanView> allPlans() {
        return plans.findAllByOrderBySortOrderAsc().stream().map(CatalogService::view).toList();
    }

    @Transactional(readOnly = true)
    public List<CourseView> courses() {
        return courses.findByActiveTrueOrderByTitleAsc().stream()
                .map(c -> new CourseView(c.getCourseRef(), c.getTitle(), c.getPricePaise(), c.getCurrency())).toList();
    }

    @Transactional
    public PlanView update(Long id, UpdatePlanRequest req) {
        Plan p = plans.findById(id).orElseThrow(() -> ApiException.notFound("Plan not found"));
        if (req.name() != null && !req.name().isBlank()) p.setName(req.name().trim());
        if (req.pricePaise() != null) {
            if ("FREE".equals(p.getPlanType()) && req.pricePaise() != 0) {
                throw ApiException.badRequest("VALIDATION_FAILED", "The Free plan must stay free");
            }
            p.setPricePaise(req.pricePaise());
        }
        if (req.active() != null) p.setActive(req.active());
        return view(p);
    }

    static PlanView view(Plan p) {
        List<String> features = p.getFeatures() == null ? List.of()
                : Arrays.stream(p.getFeatures().split("\\|")).filter(s -> !s.isBlank()).toList();
        return new PlanView(p.getId(), p.getCode(), p.getName(), p.getPlanType(), p.getPricePaise(), p.getCurrency(),
                p.getDurationDays(), p.isSelfServe(), p.getDescription(), features, p.isActive());
    }
}
