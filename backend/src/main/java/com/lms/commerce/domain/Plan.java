package com.lms.commerce.domain;

import com.lms.identity.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "plans")
@Getter @Setter
public class Plan extends BaseEntity {
    @Column(nullable = false) private String code;
    @Column(nullable = false) private String name;
    @Column(name = "plan_type", nullable = false) private String planType;
    @Column(name = "price_paise", nullable = false) private long pricePaise;
    @Column(nullable = false) private String currency = "INR";
    @Column(name = "duration_days") private Integer durationDays;
    @Column(name = "self_serve", nullable = false) private boolean selfServe = true;
    @Column(name = "grants_all_courses", nullable = false) private boolean grantsAllCourses;
    private String description;
    private String features;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(nullable = false) private boolean active = true;
}
