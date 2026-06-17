package com.dilnur.library_management.dto.request;

import java.util.UUID;

public record PayFineRequest(
        UUID fineId
) {
}