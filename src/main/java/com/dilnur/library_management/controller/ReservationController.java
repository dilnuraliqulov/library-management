package com.dilnur.library_management.controller;

import com.dilnur.library_management.dto.request.ReservationRequest;
import com.dilnur.library_management.dto.response.ReservationResponse;
import com.dilnur.library_management.exception.ErrorResponse;
import com.dilnur.library_management.service.ReservationService;
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
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reservations", description = "Endpoints for reserving and managing book reservations")
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "Reserve a book",
            description = "Places a reservation for a book that has no available copies. " +
                    "Fails if the member is blocked, already has a pending reservation for this book, " +
                    "or the book still has available copies")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Reservation created successfully",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Member or book not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Business rule violation — member blocked, duplicate reservation, or copies still available",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ReservationResponse> reserveBook(@Valid @RequestBody ReservationRequest request) {
        log.info("Received request to reserve book: memberId={}, bookId={}", request.memberId(), request.bookId());

        ReservationResponse response = reservationService.reserveBook(request);

        log.info("Reservation created successfully with id={}", response.id());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/reservations/" + response.id()))
                .body(response);
    }

    @Operation(summary = "Cancel a reservation",
            description = "Cancels a PENDING or NOTIFIED reservation. " +
                    "If the reservation was NOTIFIED, the copy is returned to the available pool")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reservation cancelled successfully",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Reservation not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Reservation is already fulfilled or cancelled",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ReservationResponse> cancelReservation(@PathVariable UUID id) {
        log.info("Received request to cancel reservation with id={}", id);

        ReservationResponse response = reservationService.cancelReservation(id);

        log.info("Reservation cancelled successfully with id={}", id);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get reservation by id", description = "Returns a single reservation by its unique identifier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reservation found",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Reservation not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getReservationById(@PathVariable UUID id) {
        log.info("Received request to fetch reservation with id={}", id);

        ReservationResponse response = reservationService.getReservationById(id);

        log.info("Reservation fetched successfully with id={}", id);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get all reservations", description = "Returns a paginated list of all reservations")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reservations fetched successfully",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class)))
    })
    @GetMapping
    public ResponseEntity<Page<ReservationResponse>> getAllReservations(
            @PageableDefault(size = 20, sort = "reservedAt") Pageable pageable) {
        log.info("Received request to fetch all reservations: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());

        Page<ReservationResponse> responses = reservationService.getAllReservations(pageable);

        log.info("Fetched {} reservations (page {} of {})", responses.getNumberOfElements(),
                pageable.getPageNumber(), responses.getTotalPages());

        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Get reservations by member", description = "Returns a paginated list of all reservations belonging to a specific member")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reservations fetched successfully",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Member not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/member/{memberId}")
    public ResponseEntity<Page<ReservationResponse>> getReservationsByMember(
            @PathVariable UUID memberId,
            @PageableDefault(size = 20, sort = "reservedAt") Pageable pageable) {
        log.info("Received request to fetch reservations for memberId={}", memberId);

        Page<ReservationResponse> responses = reservationService.getReservationsByMember(memberId, pageable);

        log.info("Fetched {} reservations for memberId={}", responses.getTotalElements(), memberId);

        return ResponseEntity.ok(responses);
    }
}