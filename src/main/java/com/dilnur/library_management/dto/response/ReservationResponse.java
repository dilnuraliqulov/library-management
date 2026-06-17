package com.dilnur.library_management.dto.response;


import com.dilnur.library_management.entity.enums.ReservationStatus;

import java.time.LocalDate;
import java.util.UUID;

public record ReservationResponse(

        UUID id,

        UUID memberId,

        String memberName,

        UUID bookId,

        String bookTitle,

        LocalDate reservedAt,

        LocalDate expiresAt,

        ReservationStatus status

) {
}