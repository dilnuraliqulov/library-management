package com.dilnur.library_management.dto.response;

import com.dilnur.library_management.entity.enums.MemberStatus;
import com.dilnur.library_management.entity.enums.MemberType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MemberResponse (
    UUID id,
    String firstName,
    String lastName,
    String email,
    LocalDate createdAt,
    MemberStatus status,
    BigDecimal unpaidFinesTotal,
    MemberType memberType
    ) {

}
