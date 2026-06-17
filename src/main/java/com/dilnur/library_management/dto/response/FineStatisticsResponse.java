package com.dilnur.library_management.dto.response;


import java.math.BigDecimal;

public record FineStatisticsResponse(

        long totalFines,

        long paidFines,

        long unpaidFines,

        BigDecimal totalAmount,

        BigDecimal paidAmount,

        BigDecimal unpaidAmount,

        BigDecimal averageFineAmount

) {
}