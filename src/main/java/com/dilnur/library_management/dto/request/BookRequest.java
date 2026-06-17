package com.dilnur.library_management.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record BookRequest(
    @NotBlank
    @Size(max = 255)
    String title,

    @NotBlank
    @Size(max = 20)
    String isbn,

    @Size(max = 100)
    String genre,


    @NotNull
    @Min(1000)
    @Max(2100)
    int publicationYear,

    @NotNull
    @Positive
    int totalCopies,

    @NotNull
    @PositiveOrZero
    int availableCopies,

    @Positive
    BigDecimal price,


    @NotEmpty
    List<UUID>authorIds
) {
}
