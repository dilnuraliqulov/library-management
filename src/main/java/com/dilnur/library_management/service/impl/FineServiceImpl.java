package com.dilnur.library_management.service.impl;

import com.dilnur.library_management.config.FineProperties;
import com.dilnur.library_management.config.LoanProperties;
import com.dilnur.library_management.config.ReservationProperties;
import com.dilnur.library_management.dto.response.FineResponse;
import com.dilnur.library_management.entity.*;
import com.dilnur.library_management.entity.enums.FineStatus;
import com.dilnur.library_management.entity.enums.LoanStatus;
import com.dilnur.library_management.entity.enums.MemberStatus;
import com.dilnur.library_management.entity.enums.ReservationStatus;
import com.dilnur.library_management.exception.BusinessRuleException;
import com.dilnur.library_management.mapper.FineMapper;
import com.dilnur.library_management.repository.FineRepository;
import com.dilnur.library_management.repository.LoanRepository;
import com.dilnur.library_management.repository.MemberRepository;
import com.dilnur.library_management.repository.ReservationRepository;
import com.dilnur.library_management.service.BookService;
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
@Transactional
public class FineServiceImpl implements FineService {

    private final FineRepository fineRepository;
    private final LoanRepository loanRepository;
    private final MemberRepository memberRepository;
    private final FineMapper fineMapper;
    private final FineProperties fineProperties;
    private final LoanProperties loanProperties;
    private final ReservationRepository reservationRepository;
    private final ReservationProperties reservationProperties;
    private final BookService bookService;


    @Override
    public FineResponse payFine(UUID fineId) {
        Fine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new EntityNotFoundException("Fine not found with id: " + fineId));

        if (fine.getStatus() == FineStatus.PAID) {
            throw new BusinessRuleException("Fine is already paid");
        }

        fine.setStatus(FineStatus.PAID);
        fineRepository.save(fine);

        Member member = fine.getLoan().getMember();

        BigDecimal actualUnpaidTotal = fineRepository.sumUnpaidByMember(member);
        member.setUnpaidFinesTotal(actualUnpaidTotal);

        if (member.getStatus() == MemberStatus.BLOCKED
                && member.getUnpaidFinesTotal().compareTo(loanProperties.getMaxUnpaidThreshold()) < 0) {
            member.setStatus(MemberStatus.ACTIVE);
            log.info("Member {} unblocked — unpaidFinesTotal={} is below threshold={}",
                    member.getId(), member.getUnpaidFinesTotal(), loanProperties.getMaxUnpaidThreshold());
        }

        memberRepository.save(member);
        return fineMapper.toResponse(fine);
    }


    @Override
    @Scheduled(cron = "0 0 0 * * *")
    public void updateOverdueFines() {
        log.info("Running daily fine update job");

        List<Loan> overdueLoans = loanRepository
                .findByStatusInAndDueDateBeforeAndReturnedAtIsNull(
                        List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE), LocalDate.now());

        for (Loan loan : overdueLoans) {
            if (loan.getStatus() == LoanStatus.ACTIVE) {
                loan.setStatus(LoanStatus.OVERDUE);
                loanRepository.save(loan);
            }
            calculateAndSaveFine(loan);
        }

        expireNotifiedReservations();

        log.info("Daily fine update job completed");
    }

    private void expireNotifiedReservations() {
        List<Reservation> expiredReservations = reservationRepository
                .findByStatusAndExpiresAtBefore(ReservationStatus.NOTIFIED, LocalDate.now());

        for (Reservation reservation : expiredReservations) {
            reservation.setStatus(ReservationStatus.CANCELLED);
            reservationRepository.save(reservation);

            Book book = reservation.getBook();
            book.setNotifiedBooks(Math.max(0, book.getNotifiedBooks() - 1));

            // notify next in queue
            List<Reservation> queue = reservationRepository
                    .findByBookAndStatusOrderByReservedAtAsc(book, ReservationStatus.PENDING);

            if (queue.isEmpty()) {
                // no one waiting — return copy to available pool
                book.setAvailableCopies(book.getAvailableCopies() + 1);
                bookService.saveBook(book);
            } else {
                // notify next member
                Reservation next = queue.get(0);
                next.setStatus(ReservationStatus.NOTIFIED);
                next.setExpiresAt(LocalDate.now().plusDays(reservationProperties.getNotificationExpiryDays()));
                reservationRepository.save(next);
                book.setNotifiedBooks(book.getNotifiedBooks() + 1);
                bookService.saveBook(book);
                log.info("Next member {} notified for book {}", next.getMember().getId(), book.getId());
            }

            log.info("Expired NOTIFIED reservation {} for member {} and book {}",
                    reservation.getId(), reservation.getMember().getId(), book.getId());
        }
    }


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
                    if (existing.getStatus() == FineStatus.PAID) {
                        log.info("Fine for loan {} is already paid — skipping recalculation", loan.getId());
                        return;
                    }

                    BigDecimal oldAmount = existing.getAmount();
                    BigDecimal delta = finalAmount.subtract(oldAmount);

                    existing.setAmount(finalAmount);
                    existing.setLastCalculatedAt(LocalDate.now());
                    existing.setCapped(finalCapped);
                    fineRepository.save(existing);

                    if (delta.compareTo(BigDecimal.ZERO) != 0) {
                        BigDecimal actualUnpaidTotal = fineRepository.sumUnpaidByMember(member);
                        member.setUnpaidFinesTotal(actualUnpaidTotal);
                        blockMemberIfThresholdExceeded(member);
                        memberRepository.save(member);
                        log.info("Updated fine for loan {}: oldAmount={}, newAmount={}",
                                loan.getId(), oldAmount, finalAmount);
                    }
                },
                () -> {
                    Fine fine = new Fine();
                    fine.setLoan(loan);
                    fine.setAmount(finalAmount);
                    fine.setStatus(FineStatus.UNPAID);
                    fine.setLastCalculatedAt(LocalDate.now());
                    fine.setCapped(finalCapped);
                    fineRepository.save(fine);

                    BigDecimal actualUnpaidTotal = fineRepository.sumUnpaidByMember(member);
                    member.setUnpaidFinesTotal(actualUnpaidTotal);
                    blockMemberIfThresholdExceeded(member);
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
    private void blockMemberIfThresholdExceeded(Member member) {
        if (member.getStatus() == MemberStatus.ACTIVE
                && member.getUnpaidFinesTotal().compareTo(loanProperties.getMaxUnpaidThreshold()) >= 0) {
            member.setStatus(MemberStatus.BLOCKED);
            log.info("Member {} automatically blocked — unpaidFinesTotal={} exceeds threshold={}",
                    member.getId(), member.getUnpaidFinesTotal(), loanProperties.getMaxUnpaidThreshold());
        }
    }
}