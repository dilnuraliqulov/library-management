package com.dilnur.library_management.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "library.reservation")
@Getter
@Setter
public class ReservationProperties {
    private int notificationExpiryDays;
}