package com.dilnur.library_management.service;

import com.dilnur.library_management.config.FineProperties;
import com.dilnur.library_management.config.LoanProperties;
import com.dilnur.library_management.config.ReservationProperties;
import com.dilnur.library_management.dto.response.FineResponse;
import com.dilnur.library_management.entity.*;
import com.dilnur.library_management.entity.enums.FineStatus;
import com.dilnur.library_management.entity.enums.LoanStatus;
import com.dilnur.library_management.entity.enums.MemberStatus;
import com.dilnur.library_management.entity.enums.MemberType;
import com.dilnur.library_management.entity.enums.ReservationStatus;
import com.dilnur.library_management.exception.BusinessRuleException;
import com.dilnur.library_management.mapper.FineMapper;
import com.dilnur.library_management.repository.FineRepository;
import com.dilnur.library_management.repository.LoanRepository;
import com.dilnur.library_management.repository.MemberRepository;
import com.dilnur.library_management.repository.ReservationRepository;
import com.dilnur.library_management.service.impl.FineServiceImpl;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FineServiceImplTest {

    @Mock FineRepository fineRepository;
    @Mock LoanRepository loanRepository;
    @Mock MemberRepository memberRepository;
    @Mock FineMapper fineMapper;
    @Mock FineProperties fineProperties;
    @Mock LoanProperties loanProperties;
    @Mock ReservationRepository reservationRepository;
    @Mock ReservationProperties reservationProperties;
    @Mock BookService bookService;

    @InjectMocks
    FineServiceImpl fineService;

    private Member member;
    private Book book;
    private Loan loan;

    private FineProperties.RateConfig standardRate() {
        FineProperties.RateConfig config = new FineProperties.RateConfig();
        config.setDailyRate(new BigDecimal("1.50"));
        config.setGraceDays(0);
        return config;
    }

    @BeforeEach
    void setUp() {
        member = new Member();
        member.setId(UUID.randomUUID());
        member.setStatus(MemberStatus.ACTIVE);
        member.setMemberType(MemberType.STANDARD);
        member.setUnpaidFinesTotal(BigDecimal.ZERO);

        book = new Book();
        book.setId(UUID.randomUUID());
        book.setTitle("Clean Code");
        book.setPrice(new BigDecimal("30.00"));

        loan = new Loan();
        loan.setId(UUID.randomUUID());
        loan.setMember(member);
        loan.setBook(book);
        loan.setStatus(LoanStatus.OVERDUE);
    }


    @Nested
    @DisplayName("calculateAndSaveFine()")
    class CalculateAndSaveFine {


        @Test
        @DisplayName("creates a new fine for an overdue loan (no existing fine)")
        void calculateAndSaveFine_createsNewFine() {
            loan.setDueDate(LocalDate.now().minusDays(4)); // 4 days × 1.50 = 6.00
            given(fineProperties.getRates()).willReturn(Map.of(MemberType.STANDARD, standardRate()));
            given(fineProperties.isMaxFineCapEnabled()).willReturn(false);
            given(fineRepository.findByLoan(loan)).willReturn(Optional.empty());
            given(fineRepository.sumUnpaidByMember(member)).willReturn(new BigDecimal("6.00"));
            given(loanProperties.getMaxUnpaidThreshold()).willReturn(new BigDecimal("15000.00"));

            fineService.calculateAndSaveFine(loan);

            ArgumentCaptor<Fine> captor = ArgumentCaptor.forClass(Fine.class);
            verify(fineRepository).save(captor.capture());
            Fine saved = captor.getValue();
            assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("6.00"));
            assertThat(saved.getStatus()).isEqualTo(FineStatus.UNPAID);
            assertThat(saved.isCapped()).isFalse();
            assertThat(saved.getLoan()).isEqualTo(loan);
        }

        @Test
        @DisplayName("updates existing fine amount when fine amount changes (idempotent)")
        void calculateAndSaveFine_updatesExistingFine() {
            loan.setDueDate(LocalDate.now().minusDays(3)); // 3 × 1.50 = 4.50

            Fine existing = new Fine();
            existing.setAmount(new BigDecimal("1.50")); // previously calculated as 1 day
            existing.setStatus(FineStatus.UNPAID);

            given(fineProperties.getRates()).willReturn(Map.of(MemberType.STANDARD, standardRate()));
            given(fineProperties.isMaxFineCapEnabled()).willReturn(false);
            given(fineRepository.findByLoan(loan)).willReturn(Optional.of(existing));
            given(fineRepository.sumUnpaidByMember(member)).willReturn(new BigDecimal("4.50"));
            given(loanProperties.getMaxUnpaidThreshold()).willReturn(new BigDecimal("15000.00"));

            fineService.calculateAndSaveFine(loan);

            verify(fineRepository).save(existing);
            assertThat(existing.getAmount()).isEqualByComparingTo(new BigDecimal("4.50"));
            assertThat(existing.getLastCalculatedAt()).isEqualTo(LocalDate.now());
            verify(memberRepository).save(member);
        }

        @Test
        @DisplayName("does NOT save member when existing fine amount has not changed (truly idempotent)")
        void calculateAndSaveFine_noChangeInAmount_memberNotSaved() {
            loan.setDueDate(LocalDate.now().minusDays(3));

            Fine existing = new Fine();
            existing.setAmount(new BigDecimal("4.50"));
            existing.setStatus(FineStatus.UNPAID);

            given(fineProperties.getRates()).willReturn(Map.of(MemberType.STANDARD, standardRate()));
            given(fineProperties.isMaxFineCapEnabled()).willReturn(false);
            given(fineRepository.findByLoan(loan)).willReturn(Optional.of(existing));

            fineService.calculateAndSaveFine(loan);

            verify(fineRepository).save(existing);
            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("skips recalculation when existing fine is already PAID")
        void calculateAndSaveFine_existingFineAlreadyPaid_skips() {
            loan.setDueDate(LocalDate.now().minusDays(3));

            Fine existing = new Fine();
            existing.setAmount(new BigDecimal("4.50"));
            existing.setStatus(FineStatus.PAID);

            given(fineProperties.getRates()).willReturn(Map.of(MemberType.STANDARD, standardRate()));
            given(fineProperties.isMaxFineCapEnabled()).willReturn(false);
            given(fineRepository.findByLoan(loan)).willReturn(Optional.of(existing));

            fineService.calculateAndSaveFine(loan);

            verify(fineRepository, never()).save(any());
            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("caps fine at book price when cap is enabled and calculated amount exceeds price")
        void calculateAndSaveFine_capEnabled_fineDoesNotExceedBookPrice() {
            loan.setDueDate(LocalDate.now().minusDays(30));
            book.setPrice(new BigDecimal("30.00"));

            given(fineProperties.getRates()).willReturn(Map.of(MemberType.STANDARD, standardRate()));
            given(fineProperties.isMaxFineCapEnabled()).willReturn(true);
            given(fineRepository.findByLoan(loan)).willReturn(Optional.empty());
            given(fineRepository.sumUnpaidByMember(member)).willReturn(new BigDecimal("30.00"));
            given(loanProperties.getMaxUnpaidThreshold()).willReturn(new BigDecimal("15000.00"));

            fineService.calculateAndSaveFine(loan);

            ArgumentCaptor<Fine> captor = ArgumentCaptor.forClass(Fine.class);
            verify(fineRepository).save(captor.capture());
            assertThat(captor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
            assertThat(captor.getValue().isCapped()).isTrue();
        }

        @Test
        @DisplayName("does not cap fine when calculated amount is below book price")
        void calculateAndSaveFine_capEnabled_amountBelowPrice_notCapped() {
            loan.setDueDate(LocalDate.now().minusDays(2));
            book.setPrice(new BigDecimal("30.00"));

            given(fineProperties.getRates()).willReturn(Map.of(MemberType.STANDARD, standardRate()));
            given(fineProperties.isMaxFineCapEnabled()).willReturn(true);
            given(fineRepository.findByLoan(loan)).willReturn(Optional.empty());
            given(fineRepository.sumUnpaidByMember(member)).willReturn(new BigDecimal("3.00"));
            given(loanProperties.getMaxUnpaidThreshold()).willReturn(new BigDecimal("15000.00"));

            fineService.calculateAndSaveFine(loan);

            ArgumentCaptor<Fine> captor = ArgumentCaptor.forClass(Fine.class);
            verify(fineRepository).save(captor.capture());
            assertThat(captor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("3.00"));
            assertThat(captor.getValue().isCapped()).isFalse();
        }

        @Test
        @DisplayName("uses returnedAt as reference date when loan has been returned")
        void calculateAndSaveFine_usesReturnedAtAsReferenceDate() {
            loan.setDueDate(LocalDate.now().minusDays(10));
            loan.setReturnedAt(LocalDate.now().minusDays(7));

            given(fineProperties.getRates()).willReturn(Map.of(MemberType.STANDARD, standardRate()));
            given(fineProperties.isMaxFineCapEnabled()).willReturn(false);
            given(fineRepository.findByLoan(loan)).willReturn(Optional.empty());
            given(fineRepository.sumUnpaidByMember(member)).willReturn(new BigDecimal("4.50"));
            given(loanProperties.getMaxUnpaidThreshold()).willReturn(new BigDecimal("15000.00"));

            fineService.calculateAndSaveFine(loan);

            ArgumentCaptor<Fine> captor = ArgumentCaptor.forClass(Fine.class);
            verify(fineRepository).save(captor.capture());
            assertThat(captor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("4.50"));
        }

        @Test
        @DisplayName("no fine applied when loan is within grace period")
        void calculateAndSaveFine_withinGracePeriod_noFineCreated() {
            FineProperties.RateConfig graceConfig = new FineProperties.RateConfig();
            graceConfig.setDailyRate(new BigDecimal("1.50"));
            graceConfig.setGraceDays(3);
            given(fineProperties.getRates()).willReturn(Map.of(MemberType.STANDARD, graceConfig));

            loan.setDueDate(LocalDate.now().minusDays(2)); // 2 days < 3-day grace → chargeableDays = 0

            fineService.calculateAndSaveFine(loan);

            verify(fineRepository, never()).save(any());
            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("increments member unpaidFinesTotal when a new fine is created")
        void calculateAndSaveFine_newFine_incrementsMemberUnpaidTotal() {
            loan.setDueDate(LocalDate.now().minusDays(4));
            member.setUnpaidFinesTotal(new BigDecimal("5.00"));

            given(fineProperties.getRates()).willReturn(Map.of(MemberType.STANDARD, standardRate()));
            given(fineProperties.isMaxFineCapEnabled()).willReturn(false);
            given(fineRepository.findByLoan(loan)).willReturn(Optional.empty());
            given(fineRepository.sumUnpaidByMember(member)).willReturn(new BigDecimal("11.00")); // 5.00 + 6.00
            given(loanProperties.getMaxUnpaidThreshold()).willReturn(new BigDecimal("15000.00"));

            fineService.calculateAndSaveFine(loan);

            ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
            verify(memberRepository).save(memberCaptor.capture());
            assertThat(memberCaptor.getValue().getUnpaidFinesTotal())
                    .isEqualByComparingTo(new BigDecimal("11.00"));
        }

        @Test
        @DisplayName("blocks member when unpaid total reaches threshold after new fine")
        void calculateAndSaveFine_newFine_blocksWhenThresholdReached() {
            loan.setDueDate(LocalDate.now().minusDays(10));
            member.setUnpaidFinesTotal(new BigDecimal("12000.00"));

            given(fineProperties.getRates()).willReturn(Map.of(MemberType.STANDARD, standardRate()));
            given(fineProperties.isMaxFineCapEnabled()).willReturn(false);
            given(fineRepository.findByLoan(loan)).willReturn(Optional.empty());
            given(fineRepository.sumUnpaidByMember(member)).willReturn(new BigDecimal("15000.00"));
            given(loanProperties.getMaxUnpaidThreshold()).willReturn(new BigDecimal("15000.00"));

            fineService.calculateAndSaveFine(loan);

            ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
            verify(memberRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(MemberStatus.BLOCKED);
        }
    }


    @Nested
    @DisplayName("payFine()")
    class PayFine {

        @Test
        @DisplayName("marks fine as PAID and recalculates member unpaid total from DB")
        void payFine_success() {
            member.setUnpaidFinesTotal(new BigDecimal("10.00"));
            Fine fine = unpaidFine(new BigDecimal("10.00"));

            given(fineRepository.findById(fine.getId())).willReturn(Optional.of(fine));
            given(fineRepository.sumUnpaidByMember(member)).willReturn(BigDecimal.ZERO);
            // member is ACTIVE → unblock branch is NOT reached → loanProperties stub NOT needed here
            given(fineMapper.toResponse(fine)).willReturn(mock(FineResponse.class));

            fineService.payFine(fine.getId());

            assertThat(fine.getStatus()).isEqualTo(FineStatus.PAID);
            assertThat(member.getUnpaidFinesTotal()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(memberRepository).save(member);
        }

        @Test
        @DisplayName("unblocks member when unpaid total drops below threshold after payment")
        void payFine_unblocksMemberWhenAllFinesPaid() {
            member.setStatus(MemberStatus.BLOCKED);
            member.setUnpaidFinesTotal(new BigDecimal("10.00"));
            Fine fine = unpaidFine(new BigDecimal("10.00"));

            given(fineRepository.findById(fine.getId())).willReturn(Optional.of(fine));
            given(fineRepository.sumUnpaidByMember(member)).willReturn(BigDecimal.ZERO);
            given(loanProperties.getMaxUnpaidThreshold()).willReturn(new BigDecimal("15000.00"));
            given(fineMapper.toResponse(fine)).willReturn(mock(FineResponse.class));

            fineService.payFine(fine.getId());

            assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
            assertThat(member.getUnpaidFinesTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("does not unblock member when other fines remain unpaid above threshold")
        void payFine_doesNotUnblockMemberWhenOtherFinesRemain() {
            member.setStatus(MemberStatus.BLOCKED);
            member.setUnpaidFinesTotal(new BigDecimal("25000.00"));
            Fine fine = unpaidFine(new BigDecimal("10000.00"));

            given(fineRepository.findById(fine.getId())).willReturn(Optional.of(fine));
            given(fineRepository.sumUnpaidByMember(member)).willReturn(new BigDecimal("15000.00"));
            given(loanProperties.getMaxUnpaidThreshold()).willReturn(new BigDecimal("15000.00"));
            given(fineMapper.toResponse(fine)).willReturn(mock(FineResponse.class));

            fineService.payFine(fine.getId());

            assertThat(member.getStatus()).isEqualTo(MemberStatus.BLOCKED);
        }

        @Test
        @DisplayName("throws BusinessRuleException when fine is already paid")
        void payFine_alreadyPaid_throws() {
            Fine fine = unpaidFine(new BigDecimal("10.00"));
            fine.setStatus(FineStatus.PAID);

            given(fineRepository.findById(fine.getId())).willReturn(Optional.of(fine));

            assertThatThrownBy(() -> fineService.payFine(fine.getId()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already paid");

            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws EntityNotFoundException when fine is not found")
        void payFine_notFound_throws() {
            UUID fineId = UUID.randomUUID();
            given(fineRepository.findById(fineId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> fineService.payFine(fineId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Fine not found");
        }
    }


    @Nested
    @DisplayName("updateOverdueFines() — scheduled job")
    class UpdateOverdueFines {

        @BeforeEach
        void stubNoExpiredReservations() {
            given(reservationRepository.findByStatusAndExpiresAtBefore(
                    ReservationStatus.NOTIFIED, LocalDate.now()))
                    .willReturn(List.of());
        }

        @Test
        @DisplayName("marks newly overdue ACTIVE loans as OVERDUE and calculates fines")
        void updateOverdueFines_processesNewlyOverdueLoans() {
            Loan overdueLoan = new Loan();
            overdueLoan.setId(UUID.randomUUID());
            overdueLoan.setMember(member);
            overdueLoan.setBook(book);
            overdueLoan.setDueDate(LocalDate.now().minusDays(3));
            overdueLoan.setStatus(LoanStatus.ACTIVE);

            given(fineProperties.getRates()).willReturn(Map.of(MemberType.STANDARD, standardRate()));
            given(fineProperties.isMaxFineCapEnabled()).willReturn(false);
            given(loanRepository.findByStatusInAndDueDateBeforeAndReturnedAtIsNull(
                    List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE), LocalDate.now()))
                    .willReturn(List.of(overdueLoan));
            given(fineRepository.findByLoan(overdueLoan)).willReturn(Optional.empty());
            given(fineRepository.sumUnpaidByMember(member)).willReturn(new BigDecimal("4.50"));
            given(loanProperties.getMaxUnpaidThreshold()).willReturn(new BigDecimal("15000.00"));

            fineService.updateOverdueFines();

            assertThat(overdueLoan.getStatus()).isEqualTo(LoanStatus.OVERDUE);
            verify(loanRepository).save(overdueLoan);
            verify(fineRepository).save(any(Fine.class));
        }

        @Test
        @DisplayName("recalculates fines for already-OVERDUE loans each day (idempotent)")
        void updateOverdueFines_recalculatesAlreadyOverdueLoans() {
            loan.setDueDate(LocalDate.now().minusDays(5));
            loan.setStatus(LoanStatus.OVERDUE);

            Fine existingFine = new Fine();
            existingFine.setAmount(new BigDecimal("4.50")); // yesterday: 3 days
            existingFine.setStatus(FineStatus.UNPAID);

            given(fineProperties.getRates()).willReturn(Map.of(MemberType.STANDARD, standardRate()));
            given(fineProperties.isMaxFineCapEnabled()).willReturn(false);
            given(loanRepository.findByStatusInAndDueDateBeforeAndReturnedAtIsNull(
                    List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE), LocalDate.now()))
                    .willReturn(List.of(loan));
            given(fineRepository.findByLoan(loan)).willReturn(Optional.of(existingFine));
            given(fineRepository.sumUnpaidByMember(member)).willReturn(new BigDecimal("7.50"));
            given(loanProperties.getMaxUnpaidThreshold()).willReturn(new BigDecimal("15000.00"));

            fineService.updateOverdueFines();

            verify(fineRepository).save(existingFine);
            assertThat(existingFine.getAmount()).isEqualByComparingTo(new BigDecimal("7.50")); // 5 × 1.50
        }

        @Test
        @DisplayName("does nothing when there are no overdue loans")
        void updateOverdueFines_noOverdueLoans_noInteraction() {
            given(loanRepository.findByStatusInAndDueDateBeforeAndReturnedAtIsNull(
                    List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE), LocalDate.now()))
                    .willReturn(List.of());

            fineService.updateOverdueFines();

            verify(fineRepository, never()).save(any());
            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("cancels expired NOTIFIED reservation and increases available copies when queue is empty")
        void updateOverdueFines_expiredReservation_noQueue_increasesAvailableCopies() {
            Reservation expired = new Reservation();
            expired.setId(UUID.randomUUID());
            expired.setBook(book);
            expired.setMember(member);
            expired.setStatus(ReservationStatus.NOTIFIED);
            expired.setExpiresAt(LocalDate.now().minusDays(1));
            book.setNotifiedBooks(1);
            book.setAvailableCopies(0);

            given(loanRepository.findByStatusInAndDueDateBeforeAndReturnedAtIsNull(
                    List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE), LocalDate.now()))
                    .willReturn(List.of());
            given(reservationRepository.findByStatusAndExpiresAtBefore(
                    ReservationStatus.NOTIFIED, LocalDate.now()))
                    .willReturn(List.of(expired));
            given(reservationRepository.findByBookAndStatusOrderByReservedAtAsc(
                    book, ReservationStatus.PENDING))
                    .willReturn(List.of());

            fineService.updateOverdueFines();

            assertThat(expired.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
            assertThat(book.getNotifiedBooks()).isEqualTo(0);
            assertThat(book.getAvailableCopies()).isEqualTo(1);
            verify(bookService).saveBook(book);
        }

        @Test
        @DisplayName("cancels expired NOTIFIED reservation and notifies next member in queue")
        void updateOverdueFines_expiredReservation_withQueue_notifiesNext() {
            Reservation expired = new Reservation();
            expired.setId(UUID.randomUUID());
            expired.setBook(book);
            expired.setMember(member);
            expired.setStatus(ReservationStatus.NOTIFIED);
            expired.setExpiresAt(LocalDate.now().minusDays(1));
            book.setNotifiedBooks(1);

            Member nextMember = new Member();
            nextMember.setId(UUID.randomUUID());

            Reservation next = new Reservation();
            next.setId(UUID.randomUUID());
            next.setBook(book);
            next.setMember(nextMember);
            next.setStatus(ReservationStatus.PENDING);

            given(loanRepository.findByStatusInAndDueDateBeforeAndReturnedAtIsNull(
                    List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE), LocalDate.now()))
                    .willReturn(List.of());
            given(reservationRepository.findByStatusAndExpiresAtBefore(
                    ReservationStatus.NOTIFIED, LocalDate.now()))
                    .willReturn(List.of(expired));
            given(reservationRepository.findByBookAndStatusOrderByReservedAtAsc(
                    book, ReservationStatus.PENDING))
                    .willReturn(List.of(next));
            given(reservationProperties.getNotificationExpiryDays()).willReturn(3);

            fineService.updateOverdueFines();

            assertThat(expired.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
            assertThat(next.getStatus()).isEqualTo(ReservationStatus.NOTIFIED);
            assertThat(next.getExpiresAt()).isEqualTo(LocalDate.now().plusDays(3));
            assertThat(book.getNotifiedBooks()).isEqualTo(1); // -1 expired +1 next = net 1
            verify(reservationRepository).save(next);
        }
    }


    private Fine unpaidFine(BigDecimal amount) {
        Fine fine = new Fine();
        fine.setId(UUID.randomUUID());
        fine.setLoan(loan);
        fine.setAmount(amount);
        fine.setStatus(FineStatus.UNPAID);
        fine.setLastCalculatedAt(LocalDate.now());
        return fine;
    }
}