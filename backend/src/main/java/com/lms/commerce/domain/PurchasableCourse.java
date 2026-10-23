package com.lms.commerce.domain;

import com.lms.identity.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Stand-in catalogue entry until Module 3 owns courses. */
@Entity @Table(name = "purchasable_courses")
@Getter @Setter
public class PurchasableCourse extends BaseEntity {
    @Column(name = "course_ref", nullable = false) private String courseRef;
    @Column(nullable = false) private String title;
    @Column(name = "price_paise", nullable = false) private long pricePaise;
    @Column(nullable = false) private String currency = "INR";
    @Column(nullable = false) private boolean active = true;
}
