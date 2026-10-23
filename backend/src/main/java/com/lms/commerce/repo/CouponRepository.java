package com.lms.commerce.repo;

import com.lms.commerce.domain.Coupon;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCodeIgnoreCase(String code);
    List<Coupon> findAllByOrderByIdDesc();

    /** Atomic: only succeeds while the redemption cap has room, so concurrent orders cannot overshoot it. */
    @Modifying
    @Query("update Coupon c set c.redeemedCount = c.redeemedCount + 1 where c.id = :id and (c.maxRedemptions is null or c.redeemedCount < c.maxRedemptions)")
    int claim(@Param("id") Long id);
}
