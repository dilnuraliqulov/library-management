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
import com.dilnur.library_management.mapper.LoanMapper;
import com.dilnur.library_management.repository.FineRepository;
import com.dilnur.library_management.repository.LoanRepository;
import com.dilnur.library_management.repository.MemberRepository;
import com.dilnur.library_management.repository.ReservationRepository;
import com.dilnur.library_management.service.BookService;
import com.dilnur.library_management.service.MemberService;
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
        book.setPrice(new BigDecimal("25.00"));

        loanRequest = new LoanRequest(activeMember.getId(), book.getId());
    }


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
        @DisplayName("throws when member is BLOCKED")
        void issueBook_blockedMember_throws() {
            given(memberService.getMemberEntityById(blockedMember.getId())).willReturn(blockedMember);

            LoanRequest request = new LoanRequest(blockedMember.getId(), book.getId());

            assertThatThrownBy(() -> loanService.issueBook(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("blocked");

            verifyNoInteractions(bookService, loanRepository);
        }

        @Test
        @DisplayName("throws when member has reached the active loan limit")
        void issueBook_loanLimitReached_throws() {
            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(loanProperties.getMaxBooksPerMember()).willReturn(3);
            given(loanRepository.countByMemberAndStatusIn(eq(activeMember), any())).willReturn(3);

            assertThatThrownBy(() -> loanService.issueBook(loanRequest))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("maximum number of borrowed books");

            verifyNoInteractions(bookService);
        }

        @Test
        @DisplayName("throws when member has unpaid fines exceeding the threshold")
        void issueBook_unpaidFinesExceedThreshold_throws() {
            activeMember.setUnpaidFinesTotal(new BigDecimal("25.00"));

            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(loanProperties.getMaxBooksPerMember()).willReturn(5);
            given(loanProperties.getMaxUnpaidThreshold()).willReturn(new BigDecimal("20.00"));
            given(loanRepository.countByMemberAndStatusIn(eq(activeMember), any())).willReturn(0);

            assertThatThrownBy(() -> loanService.issueBook(loanRequest))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unpaid fines");

            verifyNoInteractions(bookService);
        }

        @Test
        @DisplayName("throws when book has no available copies")
        void issueBook_noAvailableCopies_throws() {
            book.setAvailableCopies(0);

            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(loanProperties.getMaxBooksPerMember()).willReturn(5);
            given(loanProperties.getMaxUnpaidThreshold()).willReturn(new BigDecimal("20.00"));
            given(loanRepository.countByMemberAndStatusIn(eq(activeMember), any())).willReturn(0);
            given(bookService.getBookEntityById(book.getId())).willReturn(book);

            assertThatThrownBy(() -> loanService.issueBook(loanRequest))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No available copies");

            verify(bookService, never()).decreaseAvailableCopies(any());
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

            Loan savedLoan = new Loan();
            given(loanRepository.save(any(Loan.class))).willReturn(savedLoan);
            given(loanMapper.toResponse(savedLoan)).willReturn(mock(LoanResponse.class));

            loanService.issueBook(loanRequest);

            ArgumentCaptor<Loan> captor = ArgumentCaptor.forClass(Loan.class);
            verify(loanRepository).save(captor.capture());
            assertThat(captor.getValue().getDueDate()).isEqualTo(LocalDate.now().plusDays(21));
        }
    }


    @Nested
    @DisplayName("returnBook()")
    class ReturnBook {

        @Test
        @DisplayName("successfully returns book and closes the loan")
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
            verify(fineRepository, never()).save(any());
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

            FineProperties.RateConfig rateConfig = new FineProperties.RateConfig();
            rateConfig.setDailyRate(new BigDecimal("1.50"));
            rateConfig.setGraceDays(0);
            given(fineProperties.getRates()).willReturn(Map.of(MemberType.STANDARD, rateConfig));
            given(fineProperties.isMaxFineCapEnabled()).willReturn(false);
            given(fineRepository.findByLoan(loan)).willReturn(Optional.empty());

            loanService.returnBook(activeMember.getId(), book.getId());

            ArgumentCaptor<Fine> fineCaptor = ArgumentCaptor.forClass(Fine.class);
            verify(fineRepository).save(fineCaptor.capture());
            Fine savedFine = fineCaptor.getValue();
            assertThat(savedFine.getAmount()).isEqualByComparingTo(new BigDecimal("7.50")); // 5 days × 1.50
            assertThat(savedFine.getStatus()).isEqualTo(FineStatus.UNPAID);
        }

        @Test
        @DisplayName("fine is capped at book price when cap is enabled and amount exceeds price")
        void returnBook_overdue_fineIsCappedAtBookPrice() {
            Loan loan = activeLoan(LocalDate.now().minusDays(30)); // heavily overdue
            book.setPrice(new BigDecimal("10.00"));

            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(bookService.getBookEntityById(book.getId())).willReturn(book);
            given(loanRepository.findByMemberAndBookAndStatusIn(eq(activeMember), eq(book), any()))
                    .willReturn(Optional.of(loan));
            given(loanRepository.save(loan)).willReturn(loan);
            given(loanMapper.toResponse(loan)).willReturn(mock(LoanResponse.class));

            FineProperties.RateConfig rateConfig = new FineProperties.RateConfig();
            rateConfig.setDailyRate(new BigDecimal("2.00")); // 30 days × 2.00 = 60.00 > 10.00
            rateConfig.setGraceDays(0);
            given(fineProperties.getRates()).willReturn(Map.of(MemberType.STANDARD, rateConfig));
            given(fineProperties.isMaxFineCapEnabled()).willReturn(true);
            given(fineRepository.findByLoan(loan)).willReturn(Optional.empty());

            loanService.returnBook(activeMember.getId(), book.getId());

            ArgumentCaptor<Fine> fineCaptor = ArgumentCaptor.forClass(Fine.class);
            verify(fineRepository).save(fineCaptor.capture());
            assertThat(fineCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
            assertThat(fineCaptor.getValue().isCapped()).isTrue();
        }

        @Test
        @DisplayName("fine is not applied when within grace period")
        void returnBook_overdue_withinGracePeriod_noFine() {
            Loan loan = activeLoan(LocalDate.now().minusDays(2)); // 2 days overdue

            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(bookService.getBookEntityById(book.getId())).willReturn(book);
            given(loanRepository.findByMemberAndBookAndStatusIn(eq(activeMember), eq(book), any()))
                    .willReturn(Optional.of(loan));
            given(loanRepository.save(loan)).willReturn(loan);
            given(loanMapper.toResponse(loan)).willReturn(mock(LoanResponse.class));

            FineProperties.RateConfig rateConfig = new FineProperties.RateConfig();
            rateConfig.setDailyRate(new BigDecimal("1.50"));
            rateConfig.setGraceDays(3); // grace period covers the 2-day delay
            given(fineProperties.getRates()).willReturn(Map.of(MemberType.STANDARD, rateConfig));

            loanService.returnBook(activeMember.getId(), book.getId());

            verify(fineRepository, never()).save(any());
        }

        @Test
        @DisplayName("updates existing fine instead of creating duplicate (idempotent)")
        void returnBook_overdue_updatesExistingFine() {
            Loan loan = activeLoan(LocalDate.now().minusDays(3));

            Fine existingFine = new Fine();
            existingFine.setAmount(new BigDecimal("3.00"));
            existingFine.setStatus(FineStatus.UNPAID);

            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(bookService.getBookEntityById(book.getId())).willReturn(book);
            given(loanRepository.findByMemberAndBookAndStatusIn(eq(activeMember), eq(book), any()))
                    .willReturn(Optional.of(loan));
            given(loanRepository.save(loan)).willReturn(loan);
            given(loanMapper.toResponse(loan)).willReturn(mock(LoanResponse.class));

            FineProperties.RateConfig rateConfig = new FineProperties.RateConfig();
            rateConfig.setDailyRate(new BigDecimal("1.50"));
            rateConfig.setGraceDays(0);
            given(fineProperties.getRates()).willReturn(Map.of(MemberType.STANDARD, rateConfig));
            given(fineProperties.isMaxFineCapEnabled()).willReturn(false);
            given(fineRepository.findByLoan(loan)).willReturn(Optional.of(existingFine));

            loanService.returnBook(activeMember.getId(), book.getId());

            verify(fineRepository, times(1)).save(existingFine);
            assertThat(existingFine.getAmount()).isEqualByComparingTo(new BigDecimal("4.50")); // 3 days × 1.50
        }

        @Test
        @DisplayName("throws when no active loan found for member and book")
        void returnBook_noActiveLoan_throws() {
            given(memberService.getMemberEntityById(activeMember.getId())).willReturn(activeMember);
            given(bookService.getBookEntityById(book.getId())).willReturn(book);
            given(loanRepository.findByMemberAndBookAndStatusIn(eq(activeMember), eq(book), any()))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.returnBook(activeMember.getId(), book.getId()))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Active loan not found");
        }
    }


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
            given(reservationRepository.existsByBookAndStatus(book, ReservationStatus.PENDING)).willReturn(false);
            given(loanRepository.save(loan)).willReturn(loan);
            given(loanMapper.toResponse(loan)).willReturn(mock(LoanResponse.class));

            loanService.extendDueDate(loanId);

            assertThat(loan.getDueDate()).isEqualTo(LocalDate.now().plusDays(14));
            assertThat(loan.getExtensionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("throws when loan status is not ACTIVE")
        void extendDueDate_notActive_throws() {
            UUID loanId = UUID.randomUUID();
            Loan loan = activeLoan(LocalDate.now().plusDays(7));
            loan.setId(loanId);
            loan.setStatus(LoanStatus.RETURNED);

            given(loanRepository.findById(loanId)).willReturn(Optional.of(loan));

            assertThatThrownBy(() -> loanService.extendDueDate(loanId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only active loans");
        }

        @Test
        @DisplayName("throws when loan is already overdue")
        void extendDueDate_alreadyOverdue_throws() {
            UUID loanId = UUID.randomUUID();
            Loan loan = activeLoan(LocalDate.now().minusDays(1)); // past due
            loan.setId(loanId);

            given(loanRepository.findById(loanId)).willReturn(Optional.of(loan));

            assertThatThrownBy(() -> loanService.extendDueDate(loanId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("overdue");
        }

        @Test
        @DisplayName("throws when max extension count has been reached")
        void extendDueDate_maxExtensionsReached_throws() {
            UUID loanId = UUID.randomUUID();
            Loan loan = activeLoan(LocalDate.now().plusDays(5));
            loan.setId(loanId);
            loan.setExtensionCount(2);

            given(loanRepository.findById(loanId)).willReturn(Optional.of(loan));
            given(loanProperties.getMaxExtensions()).willReturn(2);

            assertThatThrownBy(() -> loanService.extendDueDate(loanId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Maximum number of extensions");
        }

        @Test
        @DisplayName("throws when another member has a pending reservation for the book")
        void extendDueDate_pendingReservationExists_throws() {
            UUID loanId = UUID.randomUUID();
            Loan loan = activeLoan(LocalDate.now().plusDays(5));
            loan.setId(loanId);
            loan.setExtensionCount(0);

            given(loanRepository.findById(loanId)).willReturn(Optional.of(loan));
            given(loanProperties.getMaxExtensions()).willReturn(2);
            given(reservationRepository.existsByBookAndStatus(book, ReservationStatus.PENDING)).willReturn(true);

            assertThatThrownBy(() -> loanService.extendDueDate(loanId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("another member is waiting");
        }

        @Test
        @DisplayName("throws when loan is not found")
        void extendDueDate_loanNotFound_throws() {
            UUID loanId = UUID.randomUUID();
            given(loanRepository.findById(loanId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.extendDueDate(loanId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Loan not found");
        }
    }


    private Loan activeLoan(LocalDate dueDate) {
        Loan loan = new Loan();
        loan.setId(UUID.randomUUID());
        loan.setMember(activeMember);
        loan.setBook(book);
        loan.setDueDate(dueDate);
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setExtensionCount(0);
        return loan;
    }
}