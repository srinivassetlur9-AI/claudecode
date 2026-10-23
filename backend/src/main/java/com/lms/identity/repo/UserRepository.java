package com.lms.identity.repo;

import com.lms.identity.domain.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByMobile(String mobile);

    @Query("""
        select u from User u
        where (:orgId is null or u.organizationId = :orgId)
          and (:q is null or lower(u.email) like lower(concat('%', :q, '%'))
               or lower(u.firstName) like lower(concat('%', :q, '%'))
               or lower(u.lastName) like lower(concat('%', :q, '%')))
        """)
    Page<User> search(@Param("orgId") Long orgId, @Param("q") String q, Pageable pageable);
}
