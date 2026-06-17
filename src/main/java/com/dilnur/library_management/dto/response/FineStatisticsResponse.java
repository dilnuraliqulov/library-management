package com.dilnur.library_management.dto.response;


import java.math.BigDecimal;
import java.util.Map;

public record FineStatisticsResponse(
        BigDecimal totalUnpaid,
        BigDecimal totalPaid,
        long unpaidCount,
        long paidCount,
        Map<String, BigDecimal> unpaidByMemberType
) {}
