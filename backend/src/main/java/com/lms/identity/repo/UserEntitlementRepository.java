package com.lms.identity.repo;

import com.lms.identity.domain.UserEntitlement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserEntitlementRepository extends JpaRepository<UserEntitlement, Long> {
    List<UserEntitlement> findByUserId(Long userId);
    List<UserEntitlement> findByUserIdAndSource(Long userId, String source);
}
