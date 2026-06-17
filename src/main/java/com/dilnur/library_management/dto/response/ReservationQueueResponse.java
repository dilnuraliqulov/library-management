package com.dilnur.library_management.dto.response;


import java.time.LocalDate;
import java.util.UUID;

public record ReservationQueueResponse(

        UUID reservationId,

        UUID memberId,

        String memberName,

        int position,

        LocalDate reservedAt

) {
}