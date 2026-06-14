package com.dilnur.library_management.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record AuthorResponse (
         UUID id,
         String firstName,
         String lastName,
         String bio){


}
