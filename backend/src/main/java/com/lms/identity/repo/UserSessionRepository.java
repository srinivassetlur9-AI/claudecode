package com.lms.identity.repo;

import com.lms.identity.domain.UserSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findByRefreshTokenHash(String hash);
    Optional<UserSession> findByIdAndUserId(Long id, Long userId);
    List<UserSession> findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(Long userId, Instant now);
    boolean existsByIdAndRevokedAtIsNullAndExpiresAtAfter(Long id, Instant now);

    @Modifying
    @Query("update UserSession s set s.revokedAt = :now, s.revokedReason = :reason where s.userId = :userId and s.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now, @Param("reason") String reason);
}
