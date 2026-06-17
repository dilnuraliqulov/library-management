package com.dilnur.library_management.config;

import com.dilnur.library_management.entity.enums.MemberType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "library.fine")
@Getter
@Setter
public class FineProperties {

    private Map<MemberType, RateConfig> rates;
    private boolean maxFineCapEnabled;

    @Getter
    @Setter
    public static class RateConfig {
        private BigDecimal dailyRate;
        private int graceDays;
    }
}