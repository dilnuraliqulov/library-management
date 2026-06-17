//package com.dilnur.library_management.service.impl;
//
//import com.dilnur.library_management.dto.request.LoanRequest;
//import com.dilnur.library_management.dto.response.LoanResponse;
//import com.dilnur.library_management.entity.Book;
//import com.dilnur.library_management.entity.Loan;
//import com.dilnur.library_management.entity.Member;
//import com.dilnur.library_management.entity.enums.LoanStatus;
//import com.dilnur.library_management.entity.enums.MemberStatus;
//import com.dilnur.library_management.entity.enums.ReservationStatus;
//import com.dilnur.library_management.mapper.LoanMapper;
//import com.dilnur.library_management.mapper.MemberMapper;
//import com.dilnur.library_management.repository.LoanRepository;
//import com.dilnur.library_management.repository.MemberRepository;
//import com.dilnur.library_management.repository.ReservationRepository;
//import com.dilnur.library_management.service.LoanService;
//import jakarta.persistence.EntityNotFoundException;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.Pageable;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.math.BigDecimal;
//import java.time.LocalDate;
//import java.util.List;
//import java.util.UUID;
//
//@Service
//@RequiredArgsConstructor
//@Transactional
//@Slf4j
//public class LoanServiceImpl implements LoanService {
//
//    private final LoanRepository loanRepository;
//    private final LoanMapper loanMapper;
//    private final MemberServiceImpl memberService;
//    private final BookServiceImpl bookService;
//    private final ReservationRepository reservationRepository;
//    private final FineServiceImpl fineService;
//    private final MemberRepository memberRepository;
//
//    @Value("${library.loan.max-books-per-member}")
//    private int maxBooksPerMember;
//
//    @Value("${library.loan.loan-days}")
//    private int loanDays;
//
//    @Value("${library.loan.extension-days}")
//    private int extensionDays;
//
//    @Value("${library.loan.max-extensions}")
//    private int maxExtensions;
//
//    @Value("${library.fine.max-unpaid-threshold}")
//    private BigDecimal maxUnpaidFinesThreshold;
//
//    @Override
//    public LoanResponse issueBook(LoanRequest request) {
//
//        Member member = memberService.getMemberEntityById(request.memberId());
//
//        if (member.getStatus() == MemberStatus.BLOCKED) {
//            throw new IllegalStateException("Member is blocked and cannot borrow books.");
//        }
//
//        if (member.getUnpaidFinesTotal().compareTo(maxUnpaidFinesThreshold) >= 0) {
//            throw new IllegalStateException("Member has unpaid fines exceeding the allowed limit.");
//        }
//
//        int activeLoans = loanRepository.countByMemberAndStatusIn(
//                member, List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE));
//
//        if (activeLoans >= maxBooksPerMember) {
//            throw new IllegalStateException("Member has reached the maximum number of borrowed books.");
//        }
//
//        Book book = bookService.getBookEntityById(request.bookId());
//
//        if (book.getAvailableCopies() <= 0) {
//            throw new IllegalStateException("No available copies for book: " + book.getTitle());
//        }
//
//        Loan loan = new Loan();
//        loan.setMember(member);
//        loan.setBook(book);
//        loan.setLoanedAt(LocalDate.now());
//        loan.setDueDate(LocalDate.now().plusDays(loanDays));
//        loan.setStatus(LoanStatus.ACTIVE);
//
//        bookService.decreaseAvailableCopies(book.getId());
//
//        Loan saved = loanRepository.save(loan);
//        return loanMapper.toResponse(saved);
//    }
//
//    @Override
//    public LoanResponse returnBook(UUID memberId, UUID bookId) {
//        Member member = memberService.getMemberEntityById(memberId);
//        Book book = bookService.getBookEntityById(bookId);
//
//        Loan loan = loanRepository.findByMemberAndBookAndStatusIn(
//                        member, book, List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE))
//                .orElseThrow(() -> new EntityNotFoundException("Active loan not found for member and book"));
//
//        loan.setReturnedAt(LocalDate.now());
//        loan.setStatus(LoanStatus.RETURNED);
//
//        if (LocalDate.now().isAfter(loan.getDueDate())) {
//            fineService.calculateAndApplyFine(loan);
//        }
//
//        Loan updated = loanRepository.save(loan);
//
//        bookService.increaseAvailableCopiesOrFulfillReservation(book.getId());
//
//        return loanMapper.toResponse(updated);
//    }
//
//    @Override
//    public LoanResponse extendDueDate(UUID loanId) {
//        Loan loan = loanRepository.findById(loanId)
//                .orElseThrow(() -> new EntityNotFoundException("Loan not found with id: " + loanId));
//
//        if (loan.getStatus() != LoanStatus.ACTIVE) {
//            throw new IllegalStateException("Only active loans can be extended.");
//        }
//
//        if (LocalDate.now().isAfter(loan.getDueDate())) {
//            throw new IllegalStateException("Cannot extend an already overdue loan.");
//        }
//
//        if (loan.getExtensionCount() >= maxExtensions) {
//            throw new IllegalStateException("Maximum number of extensions reached for this loan.");
//        }
//
//        boolean hasPendingReservation = reservationRepository
//                .existsByBookIdAndStatus(loan.getBook().getId(), ReservationStatus.PENDING);
//
//        if (hasPendingReservation) {
//            throw new IllegalStateException("Cannot extend: another member is waiting for this book.");
//        }
//
//        loan.setDueDate(loan.getDueDate().plusDays(extensionDays));
//        loan.setExtensionCount(loan.getExtensionCount() + 1);
//
//        Loan updated = loanRepository.save(loan);
//        return loanMapper.toResponse(updated);
//    }
//
//    @Override
//    public LoanResponse getLoanById(UUID id) {
//        Loan loan = loanRepository.findById(id)
//                .orElseThrow(() -> new EntityNotFoundException("Loan not found with id: " + id));
//        return loanMapper.toResponse(loan);
//    }
//
//    @Override
//    public Page<LoanResponse> getAllLoans(Pageable pageable) {
//        Page<Loan> loans = loanRepository.findAll(pageable);
//        return loanMapper.toResponsePage(loans);
//    }
//}