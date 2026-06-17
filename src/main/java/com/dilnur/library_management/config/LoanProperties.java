package com.dilnur.library_management.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "library.loan")
@Getter
@Setter
public class LoanProperties {
    private int periodDays;
    private int extensionDays;
    private int maxExtensions;
}