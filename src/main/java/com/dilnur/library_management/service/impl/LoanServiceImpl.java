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
import com.dilnur.library_management.exception.BusinessRuleException;
import com.dilnur.library_management.mapper.LoanMapper;
import com.dilnur.library_management.repository.FineRepository;
import com.dilnur.library_management.repository.LoanRepository;
import com.dilnur.library_management.repository.MemberRepository;
import com.dilnur.library_management.repository.ReservationRepository;
import com.dilnur.library_management.service.BookService;
import com.dilnur.library_management.service.FineService;
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
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class LoanServiceImpl implements LoanService {

    private final LoanRepository loanRepository;
    private final ReservationRepository reservationRepository;
    private final MemberService memberService;
    private final BookService bookService;
    private final LoanMapper loanMapper;
    private final LoanProperties loanProperties;
    private final FineService fineService;

    @Override
    public LoanResponse issueBook(LoanRequest request) {

        Member member = memberService.getMemberEntityById(request.memberId());

        if (member.getStatus() == MemberStatus.BLOCKED) {
            throw new BusinessRuleException("Member is blocked and cannot borrow books");
        }

        int activeLoans = loanRepository.countByMemberAndStatusIn(
                member, List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE));
        if (activeLoans >= loanProperties.getMaxBooksPerMember()) {
            throw new BusinessRuleException("Member has reached the maximum number of borrowed books");
        }

        if (member.getUnpaidFinesTotal().compareTo(loanProperties.getMaxUnpaidThreshold()) >= 0) {
            throw new BusinessRuleException("Member has unpaid fines exceeding the allowed limit");
        }

        Book book = bookService.getBookEntityById(request.bookId());

        // check if member has a NOTIFIED reservation for this book
        Optional<Reservation> notifiedReservation = reservationRepository
                .findByMemberAndBookAndStatus(member, book, ReservationStatus.NOTIFIED);

        if (notifiedReservation.isPresent()) {
            // member is claiming their reserved copy — fulfill the reservation
            Reservation reservation = notifiedReservation.get();
            reservation.setStatus(ReservationStatus.FULFILLED);
            reservationRepository.save(reservation);

            // decrease notifiedBooks — this copy was being held for this member
            book.setNotifiedBooks(Math.max(0, book.getNotifiedBooks() - 1));
            bookService.saveBook(book);

            // create loan directly — no availableCopies check needed
            Loan loan = new Loan();
            loan.setMember(member);
            loan.setBook(book);
            loan.setDueDate(LocalDate.now().plusDays(loanProperties.getPeriodDays()));
            loan.setStatus(LoanStatus.ACTIVE);

            Loan saved = loanRepository.save(loan);
            log.info("Reservation FULFILLED for member {} and book {}", member.getId(), book.getId());
            return loanMapper.toResponse(saved);
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

        // delegate to FineService — single source of truth
        if (LocalDate.now().isAfter(loan.getDueDate())) {
            fineService.calculateAndSaveFine(loan);  // ← delegate, don't duplicate
        }

        bookService.increaseAvailableCopiesOrFulfillReservation(book.getId());

        return loanMapper.toResponse(updated);
    }


    @Override
    public LoanResponse extendDueDate(UUID loanId) {

        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new EntityNotFoundException("Loan not found with id: " + loanId));

        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new BusinessRuleException("Only active loans can be extended");
        }

        if (LocalDate.now().isAfter(loan.getDueDate())) {
            throw new BusinessRuleException("Cannot extend an already overdue loan");
        }

        if (loan.getExtensionCount() >= loanProperties.getMaxExtensions()) {
            throw new BusinessRuleException("Maximum number of extensions reached for this loan");
        }

        boolean hasPendingReservation = reservationRepository
                .existsByBookAndStatus(loan.getBook(), ReservationStatus.PENDING);

        if (hasPendingReservation) {
            throw new BusinessRuleException("Cannot extend — another member is waiting for this book");
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


}
