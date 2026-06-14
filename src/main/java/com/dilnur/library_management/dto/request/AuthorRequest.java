package com.dilnur.library_management.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthorRequest(
        @NotBlank @Size(max = 100)
        String firstName,
        @NotBlank @Size(max = 100)
        String lastName,
        @Size(max = 1000)
        String bio
) {
}
