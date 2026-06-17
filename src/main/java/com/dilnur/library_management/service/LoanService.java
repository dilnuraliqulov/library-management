package com.dilnur.library_management.service;

import com.dilnur.library_management.dto.request.LoanRequest;
import com.dilnur.library_management.dto.response.LoanResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface LoanService {

    LoanResponse issueBook(LoanRequest request);

    LoanResponse returnBook(UUID memberId, UUID bookId);

    LoanResponse extendDueDate(UUID loanId);

    LoanResponse getLoanById(UUID id);

    Page<LoanResponse> getAllLoans(Pageable pageable);
}
