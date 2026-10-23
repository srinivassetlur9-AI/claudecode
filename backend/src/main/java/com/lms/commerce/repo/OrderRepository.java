package com.lms.commerce.repo;

import com.lms.commerce.domain.Order;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserIdOrderByIdDesc(Long userId);

    /** Row lock: payment confirmation can arrive from the browser and the webhook at once; fulfilment must run once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> lockById(@Param("id") Long id);
    Optional<Order> findByIdAndUserId(Long id, Long userId);
    Optional<Order> findByRazorpayOrderId(String razorpayOrderId);
    List<Order> findTop100ByOrderByIdDesc();
    boolean existsByUserIdAndItemTypeAndItemRefAndStatus(Long userId, String itemType, String itemRef, String status);
}
