package com.dilnur.library_management.service.impl;

import com.dilnur.library_management.config.FineProperties;
import com.dilnur.library_management.config.LoanProperties;
import com.dilnur.library_management.dto.request.LoanRequest;
import com.dilnur.library_management.dto.response.LoanResponse;
import com.dilnur.library_management.entity.*;
import com.dilnur.library_management.entity.enums.FineStatus;
import com.dilnur.library_management.entity.enums.LoanStatus;
import com.dilnur.library_management.entity.enums.MemberStatus;
import com.dilnur.library_management.entity.enums.ReservationStatus;
import com.dilnur.library_management.mapper.LoanMapper;
import com.dilnur.library_management.repository.FineRepository;
import com.dilnur.library_management.repository.LoanRepository;
import com.dilnur.library_management.repository.MemberRepository;
import com.dilnur.library_management.repository.ReservationRepository;
import com.dilnur.library_management.service.BookService;
import com.dilnur.library_management.service.LoanService;
import com.dilnur.library_management.service.MemberService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class LoanServiceImpl implements LoanService {

    private final LoanRepository loanRepository;
    private final FineRepository fineRepository;
    private final ReservationRepository reservationRepository;
    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final BookService bookService;
    private final LoanMapper loanMapper;
    private final LoanProperties loanProperties;
    private final FineProperties fineProperties;


    @Override
    public LoanResponse issueBook(LoanRequest request) {

        Member member = memberService.getMemberEntityById(request.memberId());

        if (member.getStatus() == MemberStatus.BLOCKED) {
            throw new IllegalStateException("Member is blocked and cannot borrow books");
        }

        int activeLoans = loanRepository.countByMemberAndStatusIn(
                member, List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE));
        if (activeLoans >= loanProperties.getMaxBooksPerMember()) {
            throw new IllegalStateException("Member has reached the maximum number of borrowed books");
        }

        if (member.getUnpaidFinesTotal().compareTo(loanProperties.getMaxUnpaidThreshold()) >= 0) {
            throw new IllegalStateException("Member has unpaid fines exceeding the allowed limit");
        }

        Book book = bookService.getBookEntityById(request.bookId());

        if (book.getAvailableCopies() <= 0) {
            throw new IllegalStateException("No available copies for book: " + book.getTitle());
        }

        Loan loan = new Loan();
        loan.setMember(member);
        loan.setBook(book);
        loan.setDueDate(LocalDate.now().plusDays(loanProperties.getPeriodDays()));
        loan.setStatus(LoanStatus.ACTIVE);

        bookService.decreaseAvailableCopies(book.getId());

        Loan saved = loanRepository.save(loan);
        return loanMapper.toResponse(saved);
    }


    @Override
    public LoanResponse returnBook(UUID memberId, UUID bookId) {

        Member member = memberService.getMemberEntityById(memberId);
        Book book = bookService.getBookEntityById(bookId);

        Loan loan = loanRepository.findByMemberAndBookAndStatusIn(
                        member, book, List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Active loan not found for member: " + memberId + " and book: " + bookId));

        loan.setReturnedAt(LocalDate.now());
        loan.setStatus(LoanStatus.RETURNED);
        Loan updated = loanRepository.save(loan);

        // calculate fine if overdue
        if (LocalDate.now().isAfter(loan.getDueDate())) {
            calculateAndSaveFine(loan, member, book);
        }

        // increase copies or notify next in reservation queue
        bookService.increaseAvailableCopiesOrFulfillReservation(book.getId());

        return loanMapper.toResponse(updated);
    }


    @Override
    public LoanResponse extendDueDate(UUID loanId) {

        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new EntityNotFoundException("Loan not found with id: " + loanId));

        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new IllegalStateException("Only active loans can be extended");
        }

        if (LocalDate.now().isAfter(loan.getDueDate())) {
            throw new IllegalStateException("Cannot extend an already overdue loan");
        }

        if (loan.getExtensionCount() >= loanProperties.getMaxExtensions()) {
            throw new IllegalStateException("Maximum number of extensions reached for this loan");
        }

        boolean hasPendingReservation = reservationRepository
                .existsByBookAndStatus(loan.getBook(), ReservationStatus.PENDING);

        if (hasPendingReservation) {
            throw new IllegalStateException("Cannot extend — another member is waiting for this book");
        }

        loan.setDueDate(loan.getDueDate().plusDays(loanProperties.getExtensionDays()));
        loan.setExtensionCount(loan.getExtensionCount() + 1);

        Loan updated = loanRepository.save(loan);
        return loanMapper.toResponse(updated);
    }

    // General read

    @Override
    public LoanResponse getLoanById(UUID id) {
        Loan loan = loanRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Loan not found with id: " + id));
        return loanMapper.toResponse(loan);
    }

    @Override
    public Page<LoanResponse> getAllLoans(Pageable pageable) {
        return loanMapper.toResponsePage(loanRepository.findAll(pageable));
    }

    // Private helpers
    private void calculateAndSaveFine(Loan loan, Member member, Book book) {
        FineProperties.RateConfig rateConfig = fineProperties.getRates().get(member.getMemberType());

        long overdueDays = ChronoUnit.DAYS.between(loan.getDueDate(), LocalDate.now());
        long chargeableDays = Math.max(0, overdueDays - rateConfig.getGraceDays());

        if (chargeableDays == 0) {
            log.info("Loan {} is within grace period — no fine applied", loan.getId());
            return;
        }

        BigDecimal amount = rateConfig.getDailyRate().multiply(BigDecimal.valueOf(chargeableDays));

        // cap fine at book price
        boolean isCapped = false;
        if (fineProperties.isMaxFineCapEnabled() && amount.compareTo(book.getPrice()) >= 0) {
            amount = book.getPrice();
            isCapped = true;
        }

        final BigDecimal finalAmount = amount;
        final boolean finalCapped = isCapped;

        fineRepository.findByLoan(loan).ifPresentOrElse(
                existing -> {
                    existing.setAmount(finalAmount);
                    existing.setLastCalculatedAt(LocalDate.now());
                    existing.setCapped(finalCapped);
                    fineRepository.save(existing);
                },
                () -> {
                    Fine fine = new Fine();
                    fine.setLoan(loan);
                    fine.setAmount(finalAmount);
                    fine.setStatus(FineStatus.UNPAID);
                    fine.setLastCalculatedAt(LocalDate.now());
                    fine.setCapped(finalCapped);
                    fineRepository.save(fine);

                    member.setUnpaidFinesTotal(member.getUnpaidFinesTotal().add(finalAmount));
                    memberRepository.save(member);
                }
        );
    }
}
