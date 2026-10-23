package com.lms.commerce.repo;

import com.lms.commerce.domain.Device;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    List<Device> findByUserIdOrderByRegisteredAtDesc(Long userId);
    List<Device> findByUserIdAndStatus(Long userId, String status);
    Optional<Device> findByUserIdAndDeviceKeyHash(Long userId, String hash);
    Optional<Device> findByIdAndUserId(Long id, Long userId);
    long countByUserIdAndStatus(Long userId, String status);
    List<Device> findByUserIdAndStatusAndReplacedByIsNullOrderByDeactivatedAtAsc(Long userId, String status);

    @Modifying
    @Query("update UserSession s set s.revokedAt = :now, s.revokedReason = :reason where s.deviceId = :deviceId and s.revokedAt is null")
    int revokeSessions(@Param("deviceId") Long deviceId, @Param("now") java.time.Instant now, @Param("reason") String reason);
}
