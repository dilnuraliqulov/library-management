package com.dilnur.library_management.dto.response;

import java.util.List;
import java.util.UUID;


public record BookResponse(
        UUID id,
        String title,
        String isbn,
        String genre,
        int publicationYear,
        int totalCopies,
        int availableCopies,
        int notifiedBooks,
        List<AuthorResponse>authors
) {
}
