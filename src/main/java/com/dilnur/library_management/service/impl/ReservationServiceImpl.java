package com.dilnur.library_management.service.impl;

import com.dilnur.library_management.config.ReservationProperties;
import com.dilnur.library_management.dto.request.ReservationRequest;
import com.dilnur.library_management.dto.response.ReservationResponse;
import com.dilnur.library_management.entity.*;
import com.dilnur.library_management.entity.enums.MemberStatus;
import com.dilnur.library_management.entity.enums.ReservationStatus;
import com.dilnur.library_management.mapper.ReservationMapper;
import com.dilnur.library_management.repository.ReservationRepository;
import com.dilnur.library_management.service.BookService;
import com.dilnur.library_management.service.MemberService;
import com.dilnur.library_management.service.ReservationService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final MemberService memberService;
    private final BookService bookService;
    private final ReservationMapper reservationMapper;
    private final ReservationProperties reservationProperties;


    @Override
    public ReservationResponse reserveBook(ReservationRequest request) {

        Member member = memberService.getMemberEntityById(request.memberId());
        Book book = bookService.getBookEntityById(request.bookId());

        // member must be ACTIVE to reserve
        if (member.getStatus() == MemberStatus.BLOCKED) {
            throw new IllegalStateException("Blocked members cannot make reservations");
        }

        // prevent duplicate reservation
        reservationRepository.findByMemberAndBookAndStatus(member, book, ReservationStatus.PENDING)
                .ifPresent(r -> {
                    throw new IllegalStateException("Member already has a pending reservation for this book");
                });

        // reservation only makes sense when no copies are available
        if (book.getAvailableCopies() > 0) {
            throw new IllegalStateException(
                    "Book has available copies — borrow it directly instead of reserving");
        }

        Reservation reservation = new Reservation();
        reservation.setMember(member);
        reservation.setBook(book);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setExpiresAt(null); // set later when notified

        Reservation saved = reservationRepository.save(reservation);
        log.info("Member {} reserved book {}", member.getId(), book.getId());
        return reservationMapper.toResponse(saved);
    }


    @Override
    public ReservationResponse cancelReservation(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Reservation not found with id: " + reservationId));

        if (reservation.getStatus() == ReservationStatus.FULFILLED
                || reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot cancel a reservation with status: " + reservation.getStatus());
        }

        // if reservation was NOTIFIED, the notifiedBooks count needs to decrease
        if (reservation.getStatus() == ReservationStatus.NOTIFIED) {
            Book book = reservation.getBook();
            book.setNotifiedBooks(Math.max(0, book.getNotifiedBooks() - 1));
            book.setAvailableCopies(book.getAvailableCopies() + 1);
            bookService.saveBook(book);
            log.info("Notified reservation cancelled — copy returned to available pool for book {}",
                    book.getId());
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        Reservation updated = reservationRepository.save(reservation);
        return reservationMapper.toResponse(updated);
    }

    // General read

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(UUID id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Reservation not found with id: " + id));
        return reservationMapper.toResponse(reservation);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReservationResponse> getAllReservations(Pageable pageable) {
        return reservationMapper.toResponsePage(reservationRepository.findAll(pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReservationResponse> getReservationsByMember(UUID memberId, Pageable pageable) {
        return reservationRepository.findByMemberId(memberId, pageable)
                .map(reservationMapper::toResponse);
    }
}