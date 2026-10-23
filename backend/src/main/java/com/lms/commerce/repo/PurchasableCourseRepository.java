package com.lms.commerce.repo;

import com.lms.commerce.domain.PurchasableCourse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchasableCourseRepository extends JpaRepository<PurchasableCourse, Long> {
    List<PurchasableCourse> findByActiveTrueOrderByTitleAsc();
    Optional<PurchasableCourse> findByCourseRef(String courseRef);
}
