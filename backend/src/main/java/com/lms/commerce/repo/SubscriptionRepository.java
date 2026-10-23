package com.lms.commerce.repo;

import com.lms.commerce.domain.Subscription;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByUserIdOrderByIdDesc(Long userId);
    List<Subscription> findByOrderId(Long orderId);
}
