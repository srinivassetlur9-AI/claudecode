package com.lms.identity.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "user_profiles")
@Getter @Setter
public class UserProfile extends BaseEntity {
    @Column(name = "user_id", nullable = false) private Long userId;
    private String headline;
    @Column(columnDefinition = "TEXT") private String bio;
    @Column(name = "avatar_url") private String avatarUrl;
    private String country;
    private String timezone;
}
