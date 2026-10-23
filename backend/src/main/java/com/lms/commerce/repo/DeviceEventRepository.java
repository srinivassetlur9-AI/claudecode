package com.lms.commerce.repo;

import com.lms.commerce.domain.DeviceEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceEventRepository extends JpaRepository<DeviceEvent, Long> {
    List<DeviceEvent> findTop200ByUserIdOrderByIdDesc(Long userId);
    List<DeviceEvent> findTop300ByOrderByIdDesc();
}
