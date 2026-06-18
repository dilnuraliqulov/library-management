package com.dilnur.library_management.service;

import com.dilnur.library_management.config.FineProperties;
import com.dilnur.library_management.dto.response.FineResponse;
import com.dilnur.library_management.entity.*;
import com.dilnur.library_management.entity.enums.FineStatus;
import com.dilnur.library_management.entity.enums.LoanStatus;
import com.dilnur.library_management.entity.enums.MemberStatus;
import com.dilnur.library_management.entity.enums.MemberType;
import com.dilnur.library_management.mapper.FineMapper;
import com.dilnur.library_management.repository.FineRepository;
import com.dilnur.library_management.repository.LoanRepository;
import com.dilnur.library_management.repository.MemberRepository;
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

    @InjectMocks
    FineServiceImpl fineService;


    private Member member;
    private Book book;
    private Loan loan;

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

        @BeforeEach
        void setUpRateConfig() {
            FineProperties.RateConfig config = new FineProperties.RateConfig();
            config.setDailyRate(new BigDecimal("1.50"));
            config.setGraceDays(0);
            given(fineProperties.getRates()).willReturn(Map.of(MemberType.STANDARD, config));
            given(fineProperties.isMaxFineCapEnabled()).willReturn(false);
        }

        @Test
        @DisplayName("creates a new fine for an overdue loan (no existing fine)")
        void calculateAndSaveFine_createsNewFine() {
            loan.setDueDate(LocalDate.now().minusDays(4)); // 4 days overdue

            given(fineRepository.findByLoan(loan)).willReturn(Optional.empty());

            fineService.calculateAndSaveFine(loan);

            ArgumentCaptor<Fine> captor = ArgumentCaptor.forClass(Fine.class);
            verify(fineRepository).save(captor.capture());

            Fine saved = captor.getValue();
            assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("6.00")); // 4 × 1.50
            assertThat(saved.getStatus()).isEqualTo(FineStatus.UNPAID);
            assertThat(saved.isCapped()).isFalse();
            assertThat(saved.getLoan()).isEqualTo(loan);
        }

        @Test
        @DisplayName("updates existing fine amount without creating a duplicate (idempotent)")
        void calculateAndSaveFine_updatesExistingFine() {
            loan.setDueDate(LocalDate.now().minusDays(3));

            Fine existing = new Fine();
            existing.setAmount(new BigDecimal("1.50"));
            existing.setStatus(FineStatus.UNPAID);

            given(fineRepository.findByLoan(loan)).willReturn(Optional.of(existing));

            fineService.calculateAndSaveFine(loan);

            verify(fineRepository).save(existing);
            assertThat(existing.getAmount()).isEqualByComparingTo(new BigDecimal("4.50")); // 3 × 1.50
            assertThat(existing.getLastCalculatedAt()).isEqualTo(LocalDate.now());

            // member's unpaidFinesTotal must NOT be touched again on update
            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("caps fine at book price when cap is enabled and calculated amount exceeds price")
        void calculateAndSaveFine_capEnabled_fineDoesNotExceedBookPrice() {
            loan.setDueDate(LocalDate.now().minusDays(30)); // 30 × 1.50 = 45.00 > 30.00
            book.setPrice(new BigDecimal("30.00"));

            given(fineProperties.isMaxFineCapEnabled()).willReturn(true);
            given(fineRepository.findByLoan(loan)).willReturn(Optional.empty());

            fineService.calculateAndSaveFine(loan);

            ArgumentCaptor<Fine> captor = ArgumentCaptor.forClass(Fine.class);
            verify(fineRepository).save(captor.capture());

            assertThat(captor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
            assertThat(captor.getValue().isCapped()).isTrue();
        }

        @Test
        @DisplayName("does not cap fine when calculated amount is below book price")
        void calculateAndSaveFine_capEnabled_amountBelowPrice_notCapped() {
            loan.setDueDate(LocalDate.now().minusDays(2)); // 2 × 1.50 = 3.00 < 30.00
            book.setPrice(new BigDecimal("30.00"));

            given(fineProperties.isMaxFineCapEnabled()).willReturn(true);
            given(fineRepository.findByLoan(loan)).willReturn(Optional.empty());

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
            loan.setReturnedAt(LocalDate.now().minusDays(7)); // returned 7 days after due
            // overdue = 10 - 7 = 3 days (reference is returnedAt, not today)

            given(fineRepository.findByLoan(loan)).willReturn(Optional.empty());

            fineService.calculateAndSaveFine(loan);

            ArgumentCaptor<Fine> captor = ArgumentCaptor.forClass(Fine.class);
            verify(fineRepository).save(captor.capture());

            assertThat(captor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("4.50")); // 3 × 1.50
        }

        @Test
        @DisplayName("increments member unpaidFinesTotal when a new fine is created")
        void calculateAndSaveFine_newFine_incrementsMemberUnpaidTotal() {
            loan.setDueDate(LocalDate.now().minusDays(4));
            member.setUnpaidFinesTotal(new BigDecimal("5.00"));

            given(fineRepository.findByLoan(loan)).willReturn(Optional.empty());

            fineService.calculateAndSaveFine(loan);

            ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
            verify(memberRepository).save(memberCaptor.capture());

            assertThat(memberCaptor.getValue().getUnpaidFinesTotal())
                    .isEqualByComparingTo(new BigDecimal("11.00")); // 5.00 + (4 × 1.50)
        }
    }


    @Nested
    @DisplayName("payFine()")
    class PayFine {

        @Test
        @DisplayName("marks fine as PAID and deducts amount from member unpaid total")
        void payFine_success() {
            member.setUnpaidFinesTotal(new BigDecimal("10.00"));

            Fine fine = unpaidFine(new BigDecimal("10.00"));
            UUID fineId = fine.getId();

            given(fineRepository.findById(fineId)).willReturn(Optional.of(fine));
            given(fineMapper.toResponse(fine)).willReturn(mock(FineResponse.class));

            fineService.payFine(fineId);

            assertThat(fine.getStatus()).isEqualTo(FineStatus.PAID);
            assertThat(member.getUnpaidFinesTotal()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(memberRepository).save(member);
        }

        @Test
        @DisplayName("unblocks member when all fines are paid")
        void payFine_unblocksMemberWhenAllFinesPaid() {
            member.setStatus(MemberStatus.BLOCKED);
            member.setUnpaidFinesTotal(new BigDecimal("10.00"));

            Fine fine = unpaidFine(new BigDecimal("10.00"));
            UUID fineId = fine.getId();

            given(fineRepository.findById(fineId)).willReturn(Optional.of(fine));
            given(fineMapper.toResponse(fine)).willReturn(mock(FineResponse.class));

            fineService.payFine(fineId);

            assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
            assertThat(member.getUnpaidFinesTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("does not unblock member when other fines remain unpaid")
        void payFine_doesNotUnblockMemberWhenOtherFinesRemain() {
            member.setStatus(MemberStatus.BLOCKED);
            member.setUnpaidFinesTotal(new BigDecimal("25.00"));

            Fine fine = unpaidFine(new BigDecimal("10.00")); // 15.00 still remains
            UUID fineId = fine.getId();

            given(fineRepository.findById(fineId)).willReturn(Optional.of(fine));
            given(fineMapper.toResponse(fine)).willReturn(mock(FineResponse.class));

            fineService.payFine(fineId);

            assertThat(member.getStatus()).isEqualTo(MemberStatus.BLOCKED);
            assertThat(member.getUnpaidFinesTotal()).isEqualByComparingTo(new BigDecimal("15.00"));
        }

        @Test
        @DisplayName("throws when fine is already paid")
        void payFine_alreadyPaid_throws() {
            Fine fine = unpaidFine(new BigDecimal("10.00"));
            fine.setStatus(FineStatus.PAID);

            given(fineRepository.findById(fine.getId())).willReturn(Optional.of(fine));

            assertThatThrownBy(() -> fineService.payFine(fine.getId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already paid");

            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws when fine is not found")
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

        @Test
        @DisplayName("marks newly overdue loans as OVERDUE and calculates fines")
        void updateOverdueFines_processesNewlyOverdueLoans() {
            Loan overdueloan = new Loan();
            overdueloan.setId(UUID.randomUUID());
            overdueloan.setMember(member);
            overdueloan.setBook(book);
            overdueloan.setDueDate(LocalDate.now().minusDays(3));
            overdueloan.setStatus(LoanStatus.ACTIVE);

            FineProperties.RateConfig config = new FineProperties.RateConfig();
            config.setDailyRate(new BigDecimal("1.50"));
            config.setGraceDays(0);
            given(fineProperties.getRates()).willReturn(Map.of(MemberType.STANDARD, config));
            given(fineProperties.isMaxFineCapEnabled()).willReturn(false);

            given(loanRepository.findByStatusAndDueDateBefore(LoanStatus.ACTIVE, LocalDate.now()))
                    .willReturn(List.of(overdueloan));
            given(loanRepository.findByStatus(LoanStatus.OVERDUE)).willReturn(List.of());
            given(fineRepository.findByLoan(overdueloan)).willReturn(Optional.empty());

            fineService.updateOverdueFines();

            assertThat(overdueloan.getStatus()).isEqualTo(LoanStatus.OVERDUE);
            verify(loanRepository).save(overdueloan);
            verify(fineRepository).save(any(Fine.class));
        }

        @Test
        @DisplayName("recalculates fines for already-OVERDUE loans (idempotent)")
        void updateOverdueFines_recalculatesAlreadyOverdueLoans() {
            loan.setDueDate(LocalDate.now().minusDays(5));
            loan.setStatus(LoanStatus.OVERDUE);

            Fine existingFine = new Fine();
            existingFine.setAmount(new BigDecimal("4.50")); // calculated yesterday
            existingFine.setStatus(FineStatus.UNPAID);

            FineProperties.RateConfig config = new FineProperties.RateConfig();
            config.setDailyRate(new BigDecimal("1.50"));
            config.setGraceDays(0);
            given(fineProperties.getRates()).willReturn(Map.of(MemberType.STANDARD, config));
            given(fineProperties.isMaxFineCapEnabled()).willReturn(false);

            given(loanRepository.findByStatusAndDueDateBefore(LoanStatus.ACTIVE, LocalDate.now()))
                    .willReturn(List.of());
            given(loanRepository.findByStatus(LoanStatus.OVERDUE)).willReturn(List.of(loan));
            given(fineRepository.findByLoan(loan)).willReturn(Optional.of(existingFine));

            fineService.updateOverdueFines();

            verify(fineRepository).save(existingFine);
            assertThat(existingFine.getAmount()).isEqualByComparingTo(new BigDecimal("7.50")); // 5 × 1.50
        }

        @Test
        @DisplayName("does nothing when there are no overdue loans")
        void updateOverdueFines_noOverdueLoans_noInteraction() {
            given(loanRepository.findByStatusAndDueDateBefore(LoanStatus.ACTIVE, LocalDate.now()))
                    .willReturn(List.of());
            given(loanRepository.findByStatus(LoanStatus.OVERDUE)).willReturn(List.of());

            fineService.updateOverdueFines();

            verify(fineRepository, never()).save(any());
            verify(memberRepository, never()).save(any());
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