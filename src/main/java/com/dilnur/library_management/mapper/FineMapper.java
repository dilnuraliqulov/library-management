package com.dilnur.library_management.mapper;

import com.dilnur.library_management.dto.response.FineResponse;
import com.dilnur.library_management.entity.Fine;
import com.dilnur.library_management.entity.Loan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;

@Mapper(componentModel = "spring")
public interface FineMapper {

    @Mapping(target = "loan", expression = "java(toLoanSummary(fine.getLoan()))")
    FineResponse toResponse(Fine fine);

    default FineResponse.LoanSummary toLoanSummary(Loan loan) {
        return new FineResponse.LoanSummary(
                loan.getId(),
                loan.getBook().getTitle(),
                loan.getMember().getFirstName() + " " + loan.getMember().getLastName(),
                loan.getDueDate()
        );
    }

    default Page<FineResponse> toResponsePage(Page<Fine> fines) {
        return fines.map(this::toResponse);
    }
}
