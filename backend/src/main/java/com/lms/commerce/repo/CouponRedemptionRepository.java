package com.lms.commerce.repo;

import com.lms.commerce.domain.CouponRedemption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {
    boolean existsByCouponIdAndUserId(Long couponId, Long userId);
    List<CouponRedemption> findByUserIdOrderByIdDesc(Long userId);
}
