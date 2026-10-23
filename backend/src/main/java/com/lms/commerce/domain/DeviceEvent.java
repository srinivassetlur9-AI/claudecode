package com.lms.commerce.domain;

import com.lms.identity.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "device_events")
@Getter @Setter
public class DeviceEvent extends BaseEntity {
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "device_id") private Long deviceId;
    @Column(name = "event_type", nullable = false) private String eventType;
    private String detail;
    @Column(name = "ip_address") private String ipAddress;
}
