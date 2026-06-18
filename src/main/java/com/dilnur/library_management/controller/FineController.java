package com.dilnur.library_management.controller;

import com.dilnur.library_management.dto.response.FineResponse;
import com.dilnur.library_management.exception.ErrorResponse;
import com.dilnur.library_management.service.FineService;
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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fines")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Fines", description = "Endpoints for viewing and paying overdue fines")
public class FineController {

    private final FineService fineService;

    @Operation(summary = "Get fine by id", description = "Returns a single fine by its unique identifier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fine found",
                    content = @Content(schema = @Schema(implementation = FineResponse.class))),
            @ApiResponse(responseCode = "404", description = "Fine not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<FineResponse> getFineById(@PathVariable UUID id) {
        log.info("Received request to fetch fine with id={}", id);

        FineResponse response = fineService.getFineById(id);

        log.info("Fine fetched successfully with id={}", id);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get all fines", description = "Returns a paginated list of all fines across all members")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fines fetched successfully",
                    content = @Content(schema = @Schema(implementation = FineResponse.class)))
    })
    @GetMapping
    public ResponseEntity<Page<FineResponse>> getAllFines(
            @PageableDefault(size = 20, sort = "lastCalculatedAt") Pageable pageable) {
        log.info("Received request to fetch all fines: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());

        Page<FineResponse> responses = fineService.getAllFines(pageable);

        log.info("Fetched {} fines (page {} of {})", responses.getNumberOfElements(),
                pageable.getPageNumber(), responses.getTotalPages());

        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Get fines by member", description = "Returns a paginated list of all fines belonging to a specific member")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fines fetched successfully",
                    content = @Content(schema = @Schema(implementation = FineResponse.class))),
            @ApiResponse(responseCode = "404", description = "Member not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/member/{memberId}")
    public ResponseEntity<Page<FineResponse>> getFinesByMember(
            @PathVariable UUID memberId,
            @PageableDefault(size = 20, sort = "lastCalculatedAt") Pageable pageable) {
        log.info("Received request to fetch fines for memberId={}", memberId);

        Page<FineResponse> responses = fineService.getFinesByMember(memberId, pageable);

        log.info("Fetched {} fines for memberId={}", responses.getTotalElements(), memberId);

        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Pay a fine", description = "Marks a fine as paid, deducts it from the member's unpaid total, and unblocks the member if they have no remaining unpaid fines")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fine paid successfully",
                    content = @Content(schema = @Schema(implementation = FineResponse.class))),
            @ApiResponse(responseCode = "404", description = "Fine not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Fine is already paid",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/pay")
    public ResponseEntity<FineResponse> payFine(@PathVariable UUID id) {
        log.info("Received request to pay fine with id={}", id);

        FineResponse response = fineService.payFine(id);

        log.info("Fine paid successfully with id={}", id);

        return ResponseEntity.ok(response);
    }
}