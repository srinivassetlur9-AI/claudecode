package com.lms.commerce.repo;

import com.lms.commerce.domain.Invoice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByUserIdOrderByIdDesc(Long userId);
    Optional<Invoice> findByIdAndUserId(Long id, Long userId);
    Optional<Invoice> findByOrderId(Long orderId);
}
