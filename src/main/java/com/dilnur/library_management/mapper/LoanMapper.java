package com.dilnur.library_management.mapper;

import com.dilnur.library_management.dto.response.LoanResponse;
import com.dilnur.library_management.entity.Book;
import com.dilnur.library_management.entity.Loan;
import com.dilnur.library_management.entity.Member;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;

@Mapper(componentModel = "spring")
public interface LoanMapper {

    @Mapping(target = "member", expression = "java(toMemberSummary(loan.getMember()))")
    @Mapping(target = "book", expression = "java(toBookSummary(loan.getBook()))")
    LoanResponse toResponse(Loan loan);

    default LoanResponse.MemberSummary toMemberSummary(Member member) {
        return new LoanResponse.MemberSummary(member.getId(), member.getFirstName(), member.getLastName());
    }

    default LoanResponse.BookSummary toBookSummary(Book book) {
        return new LoanResponse.BookSummary(book.getId(), book.getTitle(), book.getIsbn());
    }

    default Page<LoanResponse> toResponsePage(Page<Loan> loans) {
        return loans.map(this::toResponse);
    }
}