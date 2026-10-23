package com.lms.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Entity @Table(name = "users")
@SQLRestriction("deleted_at is null")
@Getter @Setter
public class User extends BaseEntity {
    @Column(name = "organization_id") private Long organizationId;
    @Column(nullable = false) private String email;
    private String mobile;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(name = "first_name", nullable = false) private String firstName;
    @Column(name = "last_name", nullable = false) private String lastName;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private UserStatus status = UserStatus.PENDING;
    @Column(name = "email_verified", nullable = false) private boolean emailVerified;
    @Column(name = "mobile_verified", nullable = false) private boolean mobileVerified;
    @Column(name = "failed_attempts", nullable = false) private int failedAttempts;
    @Column(name = "locked_until") private Instant lockedUntil;
    @Column(name = "last_login_at") private Instant lastLoginAt;
    @Column(nullable = false) private String language = "en";
    @Column(name = "deleted_at") private Instant deletedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<UserRoleAssignment> roleAssignments = new HashSet<>();

    public Set<Role> roles() {
        Set<Role> out = EnumSet.noneOf(Role.class);
        roleAssignments.forEach(a -> out.add(a.getRole()));
        return out;
    }

    public void replaceRoles(Set<Role> wanted) {
        roleAssignments.removeIf(a -> !wanted.contains(a.getRole()));
        Set<Role> have = roles();
        for (Role r : wanted) {
            if (!have.contains(r)) {
                UserRoleAssignment a = new UserRoleAssignment();
                a.setUser(this);
                a.setRole(r);
                roleAssignments.add(a);
            }
        }
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
