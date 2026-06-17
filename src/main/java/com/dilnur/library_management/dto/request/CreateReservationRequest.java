package com.dilnur.library_management.dto.request;


import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateReservationRequest(

        @NotNull(message = "Book id is required")
        UUID bookId

) {
}