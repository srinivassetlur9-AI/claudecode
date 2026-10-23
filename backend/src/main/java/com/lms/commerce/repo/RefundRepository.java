package com.lms.commerce.repo;

import com.lms.commerce.domain.Refund;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByUserIdOrderByIdDesc(Long userId);
    Optional<Refund> findFirstByOrderIdOrderByIdDesc(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.id = :id")
    Optional<Refund> lockById(@Param("id") Long id);
    List<Refund> findTop200ByOrderByIdDesc();
    boolean existsByOrderIdAndStatusIn(Long orderId, Collection<String> statuses);
}
