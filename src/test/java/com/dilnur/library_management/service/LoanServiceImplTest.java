package com.dilnur.library_management.service;

import com.dilnur.library_management.config.FineProperties;
import com.dilnur.library_management.config.LoanProperties;
import com.dilnur.library_management.dto.request.LoanRequest;
import com.dilnur.library_management.dto.response.LoanResponse;
import com.dilnur.library_management.entity.*;
import com.dilnur.library_management.entity.enums.FineStatus;
import com.dilnur.library_management.entity.enums.LoanStatus;
import com.dilnur.library_management.entity.enums.MemberStatus;
import com.dilnur.library_management.entity.enums.MemberType;
import com.dilnur.library_management.entity.enums.ReservationStatus;
import com.dilnur.library_management.exception.BusinessRuleException;
import com.dilnur.library_management.mapper.LoanMapper;
import com.dilnur.library_management.repository.FineRepository;
import com.dilnur.library_management.repository.LoanRepository;
import com.dilnur.library_management.repository.MemberRepository;
import com.dilnur.library_management.repository.ReservationRepository;
import com.dilnur.library_management.service.impl.LoanServiceImpl;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanServiceImplTest {

    @Mock LoanRepository loanRepository;
    @Mock FineRepository fineRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock MemberRepository memberRepository;
    @Mock MemberService memberService;
    @Mock BookService bookService;
    @Mock LoanMapper loanMapper;
    @Mock LoanProperties loanProperties;
    @Mock FineProperties fineProperties;
    @Mock FineService fineService;

    @InjectMocks
    LoanServiceImpl loanService;

    private Member activeMember;
    private Member blockedMember;
    private Book book;
    private LoanRequest loanRequest;

    @BeforeEach
    void setUp() {
        activeMember = new Member();
        activeMember.setId(UUID.randomUUID());
        activeMember.setFirstName("John");
        activeMember.setLastName("Doe");
        activeMember.setStatus(MemberStatus.ACTIVE);
        activeMember.setUnpaidFinesTotal(BigDecimal.ZERO);
        activeMember.setMemberType(MemberType.STANDARD);

        blockedMember = new Member();
        blockedMember.setId(UUID.randomUUID());
        blockedMember.setStatus(MemberStatus.BLOCKED);
        blockedMember.setUnpaidFinesTotal(BigDecimal.ZERO);
        blockedMember.setMemberType(MemberType.STANDARD);

        book = new Book();
        book.setId(UUID.randomUUID());
        book.setTitle("Clean Code");
        book.setAvailableCopies(3);
        book.setTotalCopies(5);
        book.setNotifiedBooks(0);
        book.setPrice(new BigDecimal("25.00"));

        loanRequest = new LoanRequest(activeMember.getId(), book.getId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // issueBook()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("issueBook()")
    class IssueBook {

        @Test
        @DisplayName("successfully issues book to an eligible member")
        void issueBook_success() {
            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(loanRepository.countByMemberAndStatusIn(eq(activeMember), any())).willReturn(0);
            given(bookService.getBookEntityById(book.getId())).willReturn(book);
            given(loanProperties.getMaxBooksPerMember()).willReturn(5);
            given(loanProperties.getMaxUnpaidThreshold()).willReturn(new BigDecimal("20.00"));
            given(loanProperties.getPeriodDays()).willReturn(14);
            given(reservationRepository.findByMemberAndBookAndStatus(
                    activeMember, book, ReservationStatus.NOTIFIED))
                    .willReturn(Optional.empty());

            Loan savedLoan = new Loan();
            savedLoan.setId(UUID.randomUUID());
            savedLoan.setMember(activeMember);
            savedLoan.setBook(book);
            savedLoan.setDueDate(LocalDate.now().plusDays(14));
            savedLoan.setStatus(LoanStatus.ACTIVE);

            given(loanRepository.save(any(Loan.class))).willReturn(savedLoan);
            LoanResponse expected = mock(LoanResponse.class);
            given(loanMapper.toResponse(savedLoan)).willReturn(expected);

            LoanResponse result = loanService.issueBook(loanRequest);

            assertThat(result).isEqualTo(expected);
            verify(bookService).decreaseAvailableCopies(book.getId());
            verify(loanRepository).save(any(Loan.class));
        }

        @Test
        @DisplayName("throws BusinessRuleException when member is BLOCKED")
        void issueBook_blockedMember_throws() {
            given(memberService.getMemberEntityById(blockedMember.getId())).willReturn(blockedMember);

            LoanRequest request = new LoanRequest(blockedMember.getId(), book.getId());

            // service throws BusinessRuleException, not IllegalStateException
            assertThatThrownBy(() -> loanService.issueBook(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("blocked");

            verifyNoInteractions(bookService, loanRepository);
        }

        @Test
        @DisplayName("throws BusinessRuleException when member has reached the active loan limit")
        void issueBook_loanLimitReached_throws() {
            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(loanProperties.getMaxBooksPerMember()).willReturn(3);
            given(loanRepository.countByMemberAndStatusIn(eq(activeMember), any())).willReturn(3);

            assertThatThrownBy(() -> loanService.issueBook(loanRequest))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("maximum number of borrowed books");

            verifyNoInteractions(bookService);
        }

        @Test
        @DisplayName("throws BusinessRuleException when member has unpaid fines exceeding the threshold")
        void issueBook_unpaidFinesExceedThreshold_throws() {
            activeMember.setUnpaidFinesTotal(new BigDecimal("25.00"));

            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(loanProperties.getMaxBooksPerMember()).willReturn(5);
            given(loanProperties.getMaxUnpaidThreshold()).willReturn(new BigDecimal("20.00"));
            given(loanRepository.countByMemberAndStatusIn(eq(activeMember), any())).willReturn(0);

            assertThatThrownBy(() -> loanService.issueBook(loanRequest))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("unpaid fines");

            verifyNoInteractions(bookService);
        }

        @Test
        @DisplayName("throws BusinessRuleException when book has no available copies (no reservation)")
        void issueBook_noAvailableCopies_throws() {
            book.setAvailableCopies(0);

            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(loanProperties.getMaxBooksPerMember()).willReturn(5);
            given(loanProperties.getMaxUnpaidThreshold()).willReturn(new BigDecimal("20.00"));
            given(loanRepository.countByMemberAndStatusIn(eq(activeMember), any())).willReturn(0);
            given(bookService.getBookEntityById(book.getId())).willReturn(book);
            given(reservationRepository.findByMemberAndBookAndStatus(
                    activeMember, book, ReservationStatus.NOTIFIED))
                    .willReturn(Optional.empty());

            // decreaseAvailableCopies throws BusinessRuleException when no copies available
            doThrow(new BusinessRuleException("No available copies for this book"))
                    .when(bookService).decreaseAvailableCopies(book.getId());

            assertThatThrownBy(() -> loanService.issueBook(loanRequest))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("No available copies");
        }

        @Test
        @DisplayName("sets due date based on loanProperties.getPeriodDays()")
        void issueBook_setsDueDateFromConfig() {
            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(loanRepository.countByMemberAndStatusIn(eq(activeMember), any())).willReturn(0);
            given(bookService.getBookEntityById(book.getId())).willReturn(book);
            given(loanProperties.getMaxBooksPerMember()).willReturn(5);
            given(loanProperties.getMaxUnpaidThreshold()).willReturn(new BigDecimal("20.00"));
            given(loanProperties.getPeriodDays()).willReturn(21);
            given(reservationRepository.findByMemberAndBookAndStatus(
                    activeMember, book, ReservationStatus.NOTIFIED))
                    .willReturn(Optional.empty());

            Loan savedLoan = new Loan();
            given(loanRepository.save(any(Loan.class))).willReturn(savedLoan);
            given(loanMapper.toResponse(savedLoan)).willReturn(mock(LoanResponse.class));

            loanService.issueBook(loanRequest);

            ArgumentCaptor<Loan> captor = ArgumentCaptor.forClass(Loan.class);
            verify(loanRepository).save(captor.capture());
            assertThat(captor.getValue().getDueDate()).isEqualTo(LocalDate.now().plusDays(21));
        }

        @Test
        @DisplayName("fulfills NOTIFIED reservation instead of decrementing available copies")
        void issueBook_withNotifiedReservation_fulfillsReservation() {
            Reservation reservation = new Reservation();
            reservation.setId(UUID.randomUUID());
            reservation.setMember(activeMember);
            reservation.setBook(book);
            reservation.setStatus(ReservationStatus.NOTIFIED);
            book.setNotifiedBooks(1);
            book.setAvailableCopies(0); // copy is held, not in available pool

            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(loanRepository.countByMemberAndStatusIn(eq(activeMember), any())).willReturn(0);
            given(bookService.getBookEntityById(book.getId())).willReturn(book);
            given(loanProperties.getMaxBooksPerMember()).willReturn(5);
            given(loanProperties.getMaxUnpaidThreshold()).willReturn(new BigDecimal("20.00"));
            given(loanProperties.getPeriodDays()).willReturn(14);
            given(reservationRepository.findByMemberAndBookAndStatus(
                    activeMember, book, ReservationStatus.NOTIFIED))
                    .willReturn(Optional.of(reservation));

            Loan savedLoan = new Loan();
            given(loanRepository.save(any(Loan.class))).willReturn(savedLoan);
            given(loanMapper.toResponse(savedLoan)).willReturn(mock(LoanResponse.class));

            loanService.issueBook(loanRequest);

            // reservation must be FULFILLED, not availableCopies decremented
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.FULFILLED);
            assertThat(book.getNotifiedBooks()).isEqualTo(0);
            verify(bookService, never()).decreaseAvailableCopies(any());
            verify(reservationRepository).save(reservation);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // returnBook()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("returnBook()")
    class ReturnBook {

        @Test
        @DisplayName("successfully returns book and closes the loan with no fine")
        void returnBook_success_noFine() {
            Loan loan = activeLoan(LocalDate.now().plusDays(5)); // not overdue

            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(bookService.getBookEntityById(book.getId())).willReturn(book);
            given(loanRepository.findByMemberAndBookAndStatusIn(eq(activeMember), eq(book), any()))
                    .willReturn(Optional.of(loan));
            given(loanRepository.save(loan)).willReturn(loan);
            given(loanMapper.toResponse(loan)).willReturn(mock(LoanResponse.class));

            loanService.returnBook(activeMember.getId(), book.getId());

            assertThat(loan.getStatus()).isEqualTo(LoanStatus.RETURNED);
            assertThat(loan.getReturnedAt()).isEqualTo(LocalDate.now());
            verify(bookService).increaseAvailableCopiesOrFulfillReservation(book.getId());
            // returnedAt is NOT after dueDate → fineService must NOT be called
            verify(fineService, never()).calculateAndSaveFine(any());
        }

        @Test
        @DisplayName("calculates and saves fine when returned after due date")
        void returnBook_overdue_fineCreated() {
            Loan loan = activeLoan(LocalDate.now().minusDays(5)); // 5 days overdue

            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(bookService.getBookEntityById(book.getId())).willReturn(book);
            given(loanRepository.findByMemberAndBookAndStatusIn(eq(activeMember), eq(book), any()))
                    .willReturn(Optional.of(loan));
            given(loanRepository.save(loan)).willReturn(loan);
            given(loanMapper.toResponse(loan)).willReturn(mock(LoanResponse.class));

            loanService.returnBook(activeMember.getId(), book.getId());

            // returnedAt IS after dueDate → fineService.calculateAndSaveFine must be called
            verify(fineService).calculateAndSaveFine(loan);
        }

        @Test
        @DisplayName("does not calculate fine when returned exactly on due date")
        void returnBook_returnedOnDueDate_noFine() {
            Loan loan = activeLoan(LocalDate.now()); // due today

            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(bookService.getBookEntityById(book.getId())).willReturn(book);
            given(loanRepository.findByMemberAndBookAndStatusIn(eq(activeMember), eq(book), any()))
                    .willReturn(Optional.of(loan));
            given(loanRepository.save(loan)).willReturn(loan);
            given(loanMapper.toResponse(loan)).willReturn(mock(LoanResponse.class));

            loanService.returnBook(activeMember.getId(), book.getId());

            // returnedAt == dueDate → isAfter is false → no fine
            verify(fineService, never()).calculateAndSaveFine(any());
        }

        @Test
        @DisplayName("throws EntityNotFoundException when no active loan found")
        void returnBook_noActiveLoan_throws() {
            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(bookService.getBookEntityById(book.getId())).willReturn(book);
            given(loanRepository.findByMemberAndBookAndStatusIn(eq(activeMember), eq(book), any()))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.returnBook(activeMember.getId(), book.getId()))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Active loan not found");
        }

        @Test
        @DisplayName("always calls increaseAvailableCopiesOrFulfillReservation after return, even when overdue")
        void returnBook_overdue_alwaysReleasesBookCopy() {
            Loan loan = activeLoan(LocalDate.now().minusDays(3));

            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(bookService.getBookEntityById(book.getId())).willReturn(book);
            given(loanRepository.findByMemberAndBookAndStatusIn(eq(activeMember), eq(book), any()))
                    .willReturn(Optional.of(loan));
            given(loanRepository.save(loan)).willReturn(loan);
            given(loanMapper.toResponse(loan)).willReturn(mock(LoanResponse.class));

            loanService.returnBook(activeMember.getId(), book.getId());

            verify(bookService).increaseAvailableCopiesOrFulfillReservation(book.getId());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // extendDueDate()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("extendDueDate()")
    class ExtendDueDate {

        @Test
        @DisplayName("successfully extends due date of an active loan")
        void extendDueDate_success() {
            UUID loanId = UUID.randomUUID();
            Loan loan = activeLoan(LocalDate.now().plusDays(7));
            loan.setId(loanId);
            loan.setExtensionCount(0);

            given(loanRepository.findById(loanId)).willReturn(Optional.of(loan));
            given(loanProperties.getMaxExtensions()).willReturn(2);
            given(loanProperties.getExtensionDays()).willReturn(7);
            // correct method: existsByBookAndStatusIn with a list
            given(reservationRepository.existsByBookAndStatusIn(
                    book, List.of(ReservationStatus.PENDING, ReservationStatus.NOTIFIED)))
                    .willReturn(false);
            given(loanRepository.save(loan)).willReturn(loan);
            given(loanMapper.toResponse(loan)).willReturn(mock(LoanResponse.class));

            loanService.extendDueDate(loanId);

            assertThat(loan.getDueDate()).isEqualTo(LocalDate.now().plusDays(14)); // 7 + 7
            assertThat(loan.getExtensionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("throws BusinessRuleException when loan status is not ACTIVE")
        void extendDueDate_notActive_throws() {
            UUID loanId = UUID.randomUUID();
            Loan loan = activeLoan(LocalDate.now().plusDays(7));
            loan.setId(loanId);
            loan.setStatus(LoanStatus.RETURNED);

            given(loanRepository.findById(loanId)).willReturn(Optional.of(loan));

            assertThatThrownBy(() -> loanService.extendDueDate(loanId))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Only active loans");
        }

        @Test
        @DisplayName("throws BusinessRuleException when loan is already overdue")
        void extendDueDate_alreadyOverdue_throws() {
            UUID loanId = UUID.randomUUID();
            Loan loan = activeLoan(LocalDate.now().minusDays(1)); // past due
            loan.setId(loanId);

            given(loanRepository.findById(loanId)).willReturn(Optional.of(loan));

            assertThatThrownBy(() -> loanService.extendDueDate(loanId))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("overdue");
        }

        @Test
        @DisplayName("throws BusinessRuleException when max extension count has been reached")
        void extendDueDate_maxExtensionsReached_throws() {
            UUID loanId = UUID.randomUUID();
            Loan loan = activeLoan(LocalDate.now().plusDays(5));
            loan.setId(loanId);
            loan.setExtensionCount(2);

            given(loanRepository.findById(loanId)).willReturn(Optional.of(loan));
            given(loanProperties.getMaxExtensions()).willReturn(2);

            assertThatThrownBy(() -> loanService.extendDueDate(loanId))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Maximum number of extensions");
        }

        @Test
        @DisplayName("throws BusinessRuleException when another member has a pending or notified reservation")
        void extendDueDate_pendingOrNotifiedReservationExists_throws() {
            UUID loanId = UUID.randomUUID();
            Loan loan = activeLoan(LocalDate.now().plusDays(5));
            loan.setId(loanId);
            loan.setExtensionCount(0);

            given(loanRepository.findById(loanId)).willReturn(Optional.of(loan));
            given(loanProperties.getMaxExtensions()).willReturn(2);
            // correct method: existsByBookAndStatusIn
            given(reservationRepository.existsByBookAndStatusIn(
                    book, List.of(ReservationStatus.PENDING, ReservationStatus.NOTIFIED)))
                    .willReturn(true);

            assertThatThrownBy(() -> loanService.extendDueDate(loanId))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("another member is waiting");
        }

        @Test
        @DisplayName("throws EntityNotFoundException when loan is not found")
        void extendDueDate_loanNotFound_throws() {
            UUID loanId = UUID.randomUUID();
            given(loanRepository.findById(loanId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.extendDueDate(loanId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Loan not found");
        }

        @Test
        @DisplayName("originalDueDate is never changed during extension")
        void extendDueDate_originalDueDateUnchanged() {
            UUID loanId = UUID.randomUUID();
            LocalDate original = LocalDate.now().plusDays(7);
            Loan loan = activeLoan(original);
            loan.setId(loanId);
            loan.setOriginalDueDate(original);
            loan.setExtensionCount(0);

            given(loanRepository.findById(loanId)).willReturn(Optional.of(loan));
            given(loanProperties.getMaxExtensions()).willReturn(2);
            given(loanProperties.getExtensionDays()).willReturn(7);
            given(reservationRepository.existsByBookAndStatusIn(
                    book, List.of(ReservationStatus.PENDING, ReservationStatus.NOTIFIED)))
                    .willReturn(false);
            given(loanRepository.save(loan)).willReturn(loan);
            given(loanMapper.toResponse(loan)).willReturn(mock(LoanResponse.class));

            loanService.extendDueDate(loanId);

            assertThat(loan.getOriginalDueDate()).isEqualTo(original); // must not change
            assertThat(loan.getDueDate()).isEqualTo(original.plusDays(7));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    private Loan activeLoan(LocalDate dueDate) {
        Loan loan = new Loan();
        loan.setId(UUID.randomUUID());
        loan.setMember(activeMember);
        loan.setBook(book);
        loan.setDueDate(dueDate);
        loan.setOriginalDueDate(dueDate);
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setExtensionCount(0);
        return loan;
    }
}