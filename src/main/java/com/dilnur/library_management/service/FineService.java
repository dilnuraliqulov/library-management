package com.dilnur.library_management.service;

import com.dilnur.library_management.dto.response.FineResponse;
import com.dilnur.library_management.entity.Loan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface FineService {

    FineResponse getFineById(UUID id);
    Page<FineResponse> getAllFines(Pageable pageable);
    Page<FineResponse> getFinesByMember(UUID memberId, Pageable pageable);

    FineResponse payFine(UUID fineId);

    void updateOverdueFines();

    void calculateAndSaveFine(Loan loan);
}