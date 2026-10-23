package com.lms.identity.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "user_roles")
@Getter @Setter
public class UserRoleAssignment extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private User user;
    @Enumerated(EnumType.STRING) @Column(name = "role_code", nullable = false)
    private Role role;
}
