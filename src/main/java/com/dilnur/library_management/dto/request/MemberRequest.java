package com.dilnur.library_management.dto.request;

import com.dilnur.library_management.entity.enums.MemberType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberRequest(

        @NotBlank(message = "First name must not be blank")
    @Size(max = 100)
    String firstName,

        @NotBlank(message = "Last name must not be blank")
    @Size(max = 100)
    String lastName,

        @Size(max = 150)
    @NotBlank(message = "Email must not be blank")
    @Email(message = "Email must be valid")
    String email,

        MemberType type
) {
}
