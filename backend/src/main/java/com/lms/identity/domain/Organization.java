package com.lms.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Entity @Table(name = "organizations")
@SQLRestriction("deleted_at is null")
@Getter @Setter
public class Organization extends BaseEntity {
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String type;     // PLATFORM | ORGANIZATION
    @Column(nullable = false) private String status = "ACTIVE";
    @Column(name = "deleted_at") private Instant deletedAt;
}
