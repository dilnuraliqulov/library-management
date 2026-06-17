package com.dilnur.library_management.dto.response;

import com.dilnur.library_management.entity.enums.FineStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FineResponse(
        UUID id,
        UUID loanId,
        BigDecimal amount,
        FineStatus status,
        LocalDate lastCalculatedAt,
        boolean capped
) {
}