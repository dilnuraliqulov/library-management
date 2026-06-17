package com.dilnur.library_management.dto.response;

import java.util.UUID;

public record MostBorrowedBookResponse(
        UUID id,
        String title,
        String isbn,
        int totalCopies,
        int availableCopies,
        int borrowedCopies  // totalCopies - availableCopies
) {}
