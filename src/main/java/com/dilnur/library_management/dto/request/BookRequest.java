package com.dilnur.library_management.dto.request;

import jakarta.validation.constraints.*;

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
    @Min(1)
    int totalCopies,

    @NotNull
    @Min(0)
    int availableCopies,

    @NotEmpty
    List<UUID>authorIds
) {
}
