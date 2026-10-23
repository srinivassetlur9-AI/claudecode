package com.lms.commerce.repo;

import com.lms.commerce.domain.Plan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {
    List<Plan> findByActiveTrueOrderBySortOrderAsc();
    List<Plan> findAllByOrderBySortOrderAsc();
    Optional<Plan> findByCode(String code);
}
