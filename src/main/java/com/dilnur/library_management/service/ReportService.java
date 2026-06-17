package com.dilnur.library_management.service;

import com.dilnur.library_management.dto.response.FineStatisticsResponse;
import com.dilnur.library_management.dto.response.MostBorrowedBookResponse;
import com.dilnur.library_management.dto.response.OverdueMemberResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ReportService {

    Page<MostBorrowedBookResponse> getMostBorrowedBooks(Pageable pageable);

    List<OverdueMemberResponse> getMembersWithOverdueLoans();

    FineStatisticsResponse getFineStatistics();
}