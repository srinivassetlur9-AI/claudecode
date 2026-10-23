package com.lms.identity.repo;

import com.lms.identity.domain.TokenPurpose;
import com.lms.identity.domain.VerificationToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    Optional<VerificationToken> findByTokenHashAndPurpose(String tokenHash, TokenPurpose purpose);
    Optional<VerificationToken> findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByIdDesc(Long userId, TokenPurpose purpose);

    @Modifying
    @Query("update VerificationToken t set t.usedAt = :now where t.userId = :userId and t.purpose = :purpose and t.usedAt is null")
    int invalidateOutstanding(@Param("userId") Long userId, @Param("purpose") TokenPurpose purpose, @Param("now") Instant now);
}
