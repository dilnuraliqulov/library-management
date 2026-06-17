package com.dilnur.library_management.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record OverdueMemberResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        BigDecimal unpaidFinesTotal,
        int overdueLoansCount
) {}