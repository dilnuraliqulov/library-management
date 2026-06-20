package com.dilnur.library_management.service;

import com.dilnur.library_management.dto.request.ReservationRequest;
import com.dilnur.library_management.dto.response.ReservationResponse;
import com.dilnur.library_management.entity.Book;
import com.dilnur.library_management.entity.Member;
import com.dilnur.library_management.entity.Reservation;
import com.dilnur.library_management.entity.enums.MemberStatus;
import com.dilnur.library_management.entity.enums.ReservationStatus;
import com.dilnur.library_management.exception.BusinessRuleException;
import com.dilnur.library_management.mapper.ReservationMapper;
import com.dilnur.library_management.repository.ReservationRepository;
import com.dilnur.library_management.service.impl.ReservationServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {

    @Mock ReservationRepository reservationRepository;
    @Mock MemberService memberService;
    @Mock BookService bookService;
    @Mock ReservationMapper reservationMapper;

    @InjectMocks
    ReservationServiceImpl reservationService;

    private Member activeMember;
    private Member blockedMember;
    private Book book;
    private ReservationRequest request;

    @BeforeEach
    void setUp() {
        activeMember = new Member();
        activeMember.setId(UUID.randomUUID());
        activeMember.setStatus(MemberStatus.ACTIVE);

        blockedMember = new Member();
        blockedMember.setId(UUID.randomUUID());
        blockedMember.setStatus(MemberStatus.BLOCKED);

        book = new Book();
        book.setId(UUID.randomUUID());
        book.setTitle("Clean Code");
        book.setAvailableCopies(0);
        book.setNotifiedBooks(0);

        request = new ReservationRequest(activeMember.getId(), book.getId());
    }


    @Nested
    @DisplayName("reserveBook()")
    class ReserveBook {

        @Test
        @DisplayName("successfully creates a PENDING reservation when no copies available")
        void reserveBook_success() {
            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(bookService.getBookEntityById(book.getId())).willReturn(book);
            given(reservationRepository.findByMemberAndBookAndStatus(
                    activeMember, book, ReservationStatus.PENDING))
                    .willReturn(Optional.empty());

            Reservation saved = pendingReservation(activeMember, book);
            given(reservationRepository.save(any(Reservation.class))).willReturn(saved);
            ReservationResponse expected = mock(ReservationResponse.class);
            given(reservationMapper.toResponse(saved)).willReturn(expected);

            ReservationResponse result = reservationService.reserveBook(request);

            assertThat(result).isEqualTo(expected);

            ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
            verify(reservationRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ReservationStatus.PENDING);
            assertThat(captor.getValue().getMember()).isEqualTo(activeMember);
            assertThat(captor.getValue().getBook()).isEqualTo(book);
        }

        @Test
        @DisplayName("throws BusinessRuleException when member is BLOCKED")
        void reserveBook_blockedMember_throws() {
            given(memberService.getMemberEntityById(blockedMember.getId())).willReturn(blockedMember);
            given(bookService.getBookEntityById(book.getId())).willReturn(book);

            ReservationRequest blockedRequest = new ReservationRequest(blockedMember.getId(), book.getId());

            assertThatThrownBy(() -> reservationService.reserveBook(blockedRequest))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Blocked members cannot make reservations");

            verify(reservationRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws BusinessRuleException when member already has a PENDING reservation for this book")
        void reserveBook_duplicatePendingReservation_throws() {
            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(bookService.getBookEntityById(book.getId())).willReturn(book);
            given(reservationRepository.findByMemberAndBookAndStatus(
                    activeMember, book, ReservationStatus.PENDING))
                    .willReturn(Optional.of(pendingReservation(activeMember, book)));

            assertThatThrownBy(() -> reservationService.reserveBook(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already has a pending reservation");

            verify(reservationRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws BusinessRuleException when book has available copies (should borrow directly)")
        void reserveBook_bookHasAvailableCopies_throws() {
            book.setAvailableCopies(2);

            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(bookService.getBookEntityById(book.getId())).willReturn(book);
            given(reservationRepository.findByMemberAndBookAndStatus(
                    activeMember, book, ReservationStatus.PENDING))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> reservationService.reserveBook(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("available copies");

            verify(reservationRepository, never()).save(any());
        }

        @Test
        @DisplayName("expiresAt is never set on a new PENDING reservation (server sets it only on NOTIFIED)")
        void reserveBook_expiresAtIsNull() {
            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(bookService.getBookEntityById(book.getId())).willReturn(book);
            given(reservationRepository.findByMemberAndBookAndStatus(
                    activeMember, book, ReservationStatus.PENDING))
                    .willReturn(Optional.empty());

            Reservation saved = pendingReservation(activeMember, book);
            given(reservationRepository.save(any(Reservation.class))).willReturn(saved);
            given(reservationMapper.toResponse(saved)).willReturn(mock(ReservationResponse.class));

            reservationService.reserveBook(request);

            ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
            verify(reservationRepository).save(captor.capture());
            assertThat(captor.getValue().getExpiresAt()).isNull();
        }
    }


    @Nested
    @DisplayName("cancelReservation()")
    class CancelReservation {

        @Test
        @DisplayName("successfully cancels a PENDING reservation")
        void cancelReservation_pending_success() {
            Reservation reservation = pendingReservation(activeMember, book);
            UUID reservationId = reservation.getId();

            given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));
            Reservation updated = pendingReservation(activeMember, book);
            updated.setStatus(ReservationStatus.CANCELLED);
            given(reservationRepository.save(reservation)).willReturn(updated);
            given(reservationMapper.toResponse(updated)).willReturn(mock(ReservationResponse.class));

            reservationService.cancelReservation(reservationId);

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
            // PENDING cancel → no book copy logic needed
            verify(bookService, never()).saveBook(any());
            verify(bookService, never()).increaseAvailableCopiesOrFulfillReservation(any());
        }

        @Test
        @DisplayName("cancels a NOTIFIED reservation: decrements notifiedBooks and releases copy")
        void cancelReservation_notified_decrementsNotifiedBooksAndReleasesCopy() {
            Reservation reservation = notifiedReservation(activeMember, book);
            UUID reservationId = reservation.getId();
            book.setNotifiedBooks(2);

            given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));
            given(reservationRepository.save(reservation)).willReturn(reservation);
            given(reservationMapper.toResponse(reservation)).willReturn(mock(ReservationResponse.class));

            reservationService.cancelReservation(reservationId);

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
            assertThat(book.getNotifiedBooks()).isEqualTo(1);
            verify(bookService).saveBook(book);
            verify(bookService).increaseAvailableCopiesOrFulfillReservation(book.getId());
        }

        @Test
        @DisplayName("notifiedBooks does not go below 0 when cancelling NOTIFIED reservation (Math.max guard)")
        void cancelReservation_notified_notifiedBooksFloorIsZero() {
            Reservation reservation = notifiedReservation(activeMember, book);
            book.setNotifiedBooks(0);

            given(reservationRepository.findById(reservation.getId())).willReturn(Optional.of(reservation));
            given(reservationRepository.save(reservation)).willReturn(reservation);
            given(reservationMapper.toResponse(reservation)).willReturn(mock(ReservationResponse.class));

            reservationService.cancelReservation(reservation.getId());

            assertThat(book.getNotifiedBooks()).isEqualTo(0);
        }

        @Test
        @DisplayName("throws BusinessRuleException when cancelling an already CANCELLED reservation")
        void cancelReservation_alreadyCancelled_throws() {
            Reservation reservation = pendingReservation(activeMember, book);
            reservation.setStatus(ReservationStatus.CANCELLED);

            given(reservationRepository.findById(reservation.getId()))
                    .willReturn(Optional.of(reservation));

            assertThatThrownBy(() -> reservationService.cancelReservation(reservation.getId()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Cannot cancel a reservation with status");

            verify(reservationRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws BusinessRuleException when cancelling an already FULFILLED reservation")
        void cancelReservation_alreadyFulfilled_throws() {
            Reservation reservation = pendingReservation(activeMember, book);
            reservation.setStatus(ReservationStatus.FULFILLED);

            given(reservationRepository.findById(reservation.getId()))
                    .willReturn(Optional.of(reservation));

            assertThatThrownBy(() -> reservationService.cancelReservation(reservation.getId()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Cannot cancel a reservation with status");

            verify(reservationRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws EntityNotFoundException when reservation not found")
        void cancelReservation_notFound_throws() {
            UUID reservationId = UUID.randomUUID();
            given(reservationRepository.findById(reservationId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reservationService.cancelReservation(reservationId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Reservation not found");
        }
    }


    @Nested
    @DisplayName("getReservationById()")
    class GetReservationById {

        @Test
        @DisplayName("returns reservation response when found")
        void getReservationById_found() {
            Reservation reservation = pendingReservation(activeMember, book);
            ReservationResponse expected = mock(ReservationResponse.class);

            given(reservationRepository.findById(reservation.getId()))
                    .willReturn(Optional.of(reservation));
            given(reservationMapper.toResponse(reservation)).willReturn(expected);

            ReservationResponse result = reservationService.getReservationById(reservation.getId());

            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("throws EntityNotFoundException when reservation not found")
        void getReservationById_notFound_throws() {
            UUID id = UUID.randomUUID();
            given(reservationRepository.findById(id)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reservationService.getReservationById(id))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Reservation not found");
        }
    }

    private Reservation pendingReservation(Member member, Book book) {
        Reservation r = new Reservation();
        r.setId(UUID.randomUUID());
        r.setMember(member);
        r.setBook(book);
        r.setStatus(ReservationStatus.PENDING);
        return r;
    }

    private Reservation notifiedReservation(Member member, Book book) {
        Reservation r = new Reservation();
        r.setId(UUID.randomUUID());
        r.setMember(member);
        r.setBook(book);
        r.setStatus(ReservationStatus.NOTIFIED);
        return r;
    }
}