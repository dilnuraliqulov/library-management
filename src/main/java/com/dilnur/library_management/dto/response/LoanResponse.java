package com.dilnur.library_management.dto.response;


import com.dilnur.library_management.entity.enums.LoanStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LoanResponse(
        UUID id,
        MemberSummary member,
        BookSummary book,
        LocalDate loanedAt,
        LocalDate dueDate,
        LocalDate returnedAt,
        LoanStatus status,
        LocalDate originalDueDate,
        int extensionCount


) {
    public record MemberSummary(UUID id, String firstName, String lastName) {}
    public record BookSummary(UUID id, String title, String isbn) {}
}