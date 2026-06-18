package com.dilnur.library_management.controller;

import com.dilnur.library_management.dto.request.LoanRequest;
import com.dilnur.library_management.dto.response.LoanResponse;
import com.dilnur.library_management.exception.ErrorResponse;
import com.dilnur.library_management.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Loans", description = "Endpoints for issuing, returning, and managing book loans")
public class LoanController {

    private final LoanService loanService;

    @Operation(summary = "Issue a book", description = "Creates a new loan for a member. Fails if the member is blocked, has reached the borrow limit, has unpaid fines over the threshold, or the book has no available copies")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Book issued successfully",
                    content = @Content(schema = @Schema(implementation = LoanResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Member or book not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Business rule violation — member blocked, limit reached, unpaid fines, or no copies available",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<LoanResponse> issueBook(@Valid @RequestBody LoanRequest request) {
        log.info("Received request to issue book: memberId={}, bookId={}", request.memberId(), request.bookId());

        LoanResponse response = loanService.issueBook(request);

        log.info("Book issued successfully, loanId={}", response.id());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/loans/" + response.id()))
                .body(response);
    }

    @Operation(summary = "Return a book", description = "Closes an active loan. Calculates a fine if the return is overdue, then increases available copies or notifies the next member in the reservation queue")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Book returned successfully",
                    content = @Content(schema = @Schema(implementation = LoanResponse.class))),
            @ApiResponse(responseCode = "404", description = "Member, book, or active loan not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/return")
    public ResponseEntity<LoanResponse> returnBook(
            @RequestParam UUID memberId,
            @RequestParam UUID bookId) {
        log.info("Received request to return book: memberId={}, bookId={}", memberId, bookId);

        LoanResponse response = loanService.returnBook(memberId, bookId);

        log.info("Book returned successfully, loanId={}", response.id());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Extend a loan due date", description = "Extends the due date of an active loan. Fails if the loan is overdue, the maximum number of extensions has been reached, or another member is waiting for the book")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Loan extended successfully",
                    content = @Content(schema = @Schema(implementation = LoanResponse.class))),
            @ApiResponse(responseCode = "404", description = "Loan not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Business rule violation — loan overdue, extension limit reached, or book has a pending reservation",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/extend")
    public ResponseEntity<LoanResponse> extendDueDate(@PathVariable UUID id) {
        log.info("Received request to extend due date for loanId={}", id);

        LoanResponse response = loanService.extendDueDate(id);

        log.info("Loan extended successfully, loanId={}, new dueDate={}", id, response.dueDate());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get loan by id", description = "Returns a single loan by its unique identifier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Loan found",
                    content = @Content(schema = @Schema(implementation = LoanResponse.class))),
            @ApiResponse(responseCode = "404", description = "Loan not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<LoanResponse> getLoanById(@PathVariable UUID id) {
        log.info("Received request to fetch loan with id={}", id);

        LoanResponse response = loanService.getLoanById(id);

        log.info("Loan fetched successfully with id={}", id);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get all loans", description = "Returns a paginated list of all loans")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Loans fetched successfully",
                    content = @Content(schema = @Schema(implementation = LoanResponse.class)))
    })
    @GetMapping
    public ResponseEntity<Page<LoanResponse>> getAllLoans(
            @PageableDefault(size = 20, sort = "loanedAt") Pageable pageable) {
        log.info("Received request to fetch all loans: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());

        Page<LoanResponse> responses = loanService.getAllLoans(pageable);

        log.info("Fetched {} loans (page {} of {})", responses.getNumberOfElements(),
                pageable.getPageNumber(), responses.getTotalPages());

        return ResponseEntity.ok(responses);
    }
}