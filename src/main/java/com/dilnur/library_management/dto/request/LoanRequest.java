package com.dilnur.library_management.dto.request;


import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LoanRequest(

        @NotNull(message = "Member id must not be null")
        UUID memberId,

        @NotNull(message = "Book id must not be null")
        UUID bookId
) {}