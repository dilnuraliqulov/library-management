package com.dilnur.library_management.controller;

import com.dilnur.library_management.dto.response.FineStatisticsResponse;
import com.dilnur.library_management.dto.response.MostBorrowedBookResponse;
import com.dilnur.library_management.dto.response.OverdueMemberResponse;
import com.dilnur.library_management.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reports", description = "Read-only endpoints for library statistics and reports")
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "Most borrowed books",
            description = "Returns a paginated list of books ranked by the number of times they have been borrowed")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Report fetched successfully",
                    content = @Content(schema = @Schema(implementation = MostBorrowedBookResponse.class)))
    })
    @GetMapping("/books/most-borrowed")
    public ResponseEntity<Page<MostBorrowedBookResponse>> getMostBorrowedBooks(
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("Received request for most borrowed books report: page={}, size={}",
                pageable.getPageNumber(), pageable.getPageSize());

        Page<MostBorrowedBookResponse> response = reportService.getMostBorrowedBooks(pageable);

        log.info("Most borrowed books report returned {} results", response.getTotalElements());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Members with overdue loans",
            description = "Returns a list of all members who currently have at least one overdue loan, along with their overdue loan count and unpaid fines total")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Report fetched successfully",
                    content = @Content(schema = @Schema(implementation = OverdueMemberResponse.class)))
    })
    @GetMapping("/members/overdue")
    public ResponseEntity<List<OverdueMemberResponse>> getMembersWithOverdueLoans() {
        log.info("Received request for members with overdue loans report");

        List<OverdueMemberResponse> response = reportService.getMembersWithOverdueLoans();

        log.info("Overdue members report returned {} results", response.size());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Fine statistics",
            description = "Returns aggregate fine statistics: total paid and unpaid amounts, counts, and a breakdown of unpaid fines by member type")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics fetched successfully",
                    content = @Content(schema = @Schema(implementation = FineStatisticsResponse.class)))
    })
    @GetMapping("/fines/statistics")
    public ResponseEntity<FineStatisticsResponse> getFineStatistics() {
        log.info("Received request for fine statistics report");

        FineStatisticsResponse response = reportService.getFineStatistics();

        log.info("Fine statistics report fetched successfully");

        return ResponseEntity.ok(response);
    }
}