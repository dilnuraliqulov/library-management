package com.dilnur.library_management.service.impl;

import com.dilnur.library_management.config.FineProperties;
import com.dilnur.library_management.dto.response.FineResponse;
import com.dilnur.library_management.entity.Book;
import com.dilnur.library_management.entity.Fine;
import com.dilnur.library_management.entity.Loan;
import com.dilnur.library_management.entity.Member;
import com.dilnur.library_management.entity.enums.FineStatus;
import com.dilnur.library_management.entity.enums.LoanStatus;
import com.dilnur.library_management.entity.enums.MemberStatus;
import com.dilnur.library_management.mapper.FineMapper;
import com.dilnur.library_management.repository.FineRepository;
import com.dilnur.library_management.repository.LoanRepository;
import com.dilnur.library_management.repository.MemberRepository;
import com.dilnur.library_management.service.FineService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FineServiceImpl implements FineService {

    private final FineRepository fineRepository;
    private final LoanRepository loanRepository;
    private final MemberRepository memberRepository;
    private final FineMapper fineMapper;
    private final FineProperties fineProperties;


    @Override
    public FineResponse payFine(UUID fineId) {
        Fine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new EntityNotFoundException("Fine not found with id: " + fineId));

        if (fine.getStatus() == FineStatus.PAID) {
            throw new IllegalStateException("Fine is already paid");
        }

        fine.setStatus(FineStatus.PAID);
        fineRepository.save(fine);

        // update member's unpaidFinesTotal
        Member member = fine.getLoan().getMember();
        member.setUnpaidFinesTotal(member.getUnpaidFinesTotal().subtract(fine.getAmount()));

        // if member was BLOCKED due to fines, unblock them if no more unpaid fines
        if (member.getStatus() == MemberStatus.BLOCKED
                && member.getUnpaidFinesTotal().compareTo(BigDecimal.ZERO) <= 0) {
            member.setStatus(MemberStatus.ACTIVE);
            member.setUnpaidFinesTotal(BigDecimal.ZERO); // prevent negative
            log.info("Member {} unblocked after paying all fines", member.getId());
        }

        memberRepository.save(member);
        return fineMapper.toResponse(fine);
    }

    // ─── Task 4 — Scheduled job ───────────────────────────────────────────────

    @Override
    @Scheduled(cron = "0 0 0 * * *") // runs every day at midnight
    @Transactional
    public void updateOverdueFines() {
        log.info("Running daily fine update job");

        List<Loan> overdueLoans = loanRepository
                .findByStatusAndDueDateBefore(LoanStatus.ACTIVE, LocalDate.now());

        for (Loan loan : overdueLoans) {
            // mark loan as OVERDUE — idempotent, safe to call multiple times
            loan.setStatus(LoanStatus.OVERDUE);
            loanRepository.save(loan);

            calculateAndSaveFine(loan);
        }

        // also update already-OVERDUE loans (in case job ran before but fine needs recalculation)
        List<Loan> alreadyOverdue = loanRepository.findByStatus(LoanStatus.OVERDUE);
        for (Loan loan : alreadyOverdue) {
            calculateAndSaveFine(loan);
        }

        log.info("Daily fine update job completed");
    }

    // ─── Called from LoanService on return ───────────────────────────────────

    @Override
    public void calculateAndSaveFine(Loan loan) {
        Member member = loan.getMember();
        Book book = loan.getBook();

        FineProperties.RateConfig rateConfig = fineProperties.getRates().get(member.getMemberType());

        LocalDate referenceDate = loan.getReturnedAt() != null ? loan.getReturnedAt() : LocalDate.now();
        long overdueDays = ChronoUnit.DAYS.between(loan.getDueDate(), referenceDate);
        long chargeableDays = Math.max(0, overdueDays - rateConfig.getGraceDays());

        if (chargeableDays == 0) {
            log.info("Loan {} within grace period — no fine applied", loan.getId());
            return;
        }

        BigDecimal amount = rateConfig.getDailyRate().multiply(BigDecimal.valueOf(chargeableDays));

        boolean isCapped = false;
        if (fineProperties.isMaxFineCapEnabled() && amount.compareTo(book.getPrice()) >= 0) {
            amount = book.getPrice();
            isCapped = true;
        }

        final BigDecimal finalAmount = amount;
        final boolean finalCapped = isCapped;

        fineRepository.findByLoan(loan).ifPresentOrElse(
                existing -> {
                    // only update if fine is still UNPAID — don't touch PAID fines
                    if (existing.getStatus() == FineStatus.PAID) {
                        log.info("Fine for loan {} is already paid — skipping recalculation", loan.getId());
                        return;
                    }

                    BigDecimal oldAmount = existing.getAmount();
                    BigDecimal delta = finalAmount.subtract(oldAmount); // how much the fine grew

                    existing.setAmount(finalAmount);
                    existing.setLastCalculatedAt(LocalDate.now());
                    existing.setCapped(finalCapped);
                    fineRepository.save(existing);

                    // update member's unpaidFinesTotal by the delta
                    if (delta.compareTo(BigDecimal.ZERO) != 0) {

                        member.setUnpaidFinesTotal(member.getUnpaidFinesTotal().add(delta));
                        memberRepository.save(member);
                        log.info("Updated fine for loan {}: oldAmount={}, newAmount={}, delta={}",
                                loan.getId(), oldAmount, finalAmount, delta);
                    }
                },
                () -> {
                    // fine doesn't exist — create new one (no change here)
                    Fine fine = new Fine();
                    fine.setLoan(loan);
                    fine.setAmount(finalAmount);
                    fine.setStatus(FineStatus.UNPAID);
                    fine.setLastCalculatedAt(LocalDate.now());
                    fine.setCapped(finalCapped);
                    fineRepository.save(fine);

                    member.setUnpaidFinesTotal(member.getUnpaidFinesTotal().add(finalAmount));
                    memberRepository.save(member);
                    log.info("Created fine for loan {}: amount={}", loan.getId(), finalAmount);
                }
        );
    }

    // General read

    @Override
    @Transactional(readOnly = true)
    public FineResponse getFineById(UUID id) {
        Fine fine = fineRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fine not found with id: " + id));
        return fineMapper.toResponse(fine);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FineResponse> getAllFines(Pageable pageable) {
        return fineMapper.toResponsePage(fineRepository.findAll(pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FineResponse> getFinesByMember(UUID memberId, Pageable pageable) {
        return fineRepository.findByLoanMemberId(memberId, pageable)
                .map(fineMapper::toResponse);
    }
}