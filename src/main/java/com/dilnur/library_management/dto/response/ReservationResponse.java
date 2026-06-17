package com.dilnur.library_management.dto.response;


import com.dilnur.library_management.entity.enums.ReservationStatus;

import java.time.LocalDate;
import java.util.UUID;
public record ReservationResponse(
        UUID id,
        MemberSummary member,
        BookSummary book,
        LocalDate reservedAt,
        LocalDate expiresAt,
        ReservationStatus status
) {
    public record MemberSummary(UUID id, String firstName, String lastName) {}
    public record BookSummary(UUID id, String title, String isbn) {}
}