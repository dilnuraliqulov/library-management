package com.dilnur.library_management.mapper;

import com.dilnur.library_management.dto.response.ReservationResponse;
import com.dilnur.library_management.entity.Book;
import com.dilnur.library_management.entity.Member;
import com.dilnur.library_management.entity.Reservation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;

@Mapper(componentModel = "spring")
public interface ReservationMapper {

    @Mapping(target = "member", expression = "java(toMemberSummary(reservation.getMember()))")
    @Mapping(target = "book", expression = "java(toBookSummary(reservation.getBook()))")
    ReservationResponse toResponse(Reservation reservation);

    default ReservationResponse.MemberSummary toMemberSummary(Member member) {
        return new ReservationResponse.MemberSummary(
                member.getId(),
                member.getFirstName(),
                member.getLastName()
        );
    }

    default ReservationResponse.BookSummary toBookSummary(Book book) {
        return new ReservationResponse.BookSummary(
                book.getId(),
                book.getTitle(),
                book.getIsbn()
        );
    }

    default Page<ReservationResponse> toResponsePage(Page<Reservation> reservations) {
        return reservations.map(this::toResponse);
    }
}