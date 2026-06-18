package com.dilnur.library_management.controller;

import com.dilnur.library_management.dto.request.MemberRequest;
import com.dilnur.library_management.dto.response.MemberResponse;
import com.dilnur.library_management.exception.ErrorResponse;
import com.dilnur.library_management.service.MemberService;
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
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Members", description = "Endpoints for managing library members")
public class MemberController {

    private final MemberService memberService;

    @Operation(summary = "Create a new member", description = "Registers a new library member")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Member created successfully",
                    content = @Content(schema = @Schema(implementation = MemberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already in use",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<MemberResponse> createMember(@Valid @RequestBody MemberRequest request) {
        log.info("Received request to create member: firstName={}, lastName={}", request.firstName(), request.lastName());

        MemberResponse response = memberService.createMember(request);

        log.info("Member created successfully with id={}", response.id());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/members/" + response.id()))
                .body(response);
    }

    @Operation(summary = "Get member by id", description = "Returns a single member by their unique identifier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Member found",
                    content = @Content(schema = @Schema(implementation = MemberResponse.class))),
            @ApiResponse(responseCode = "404", description = "Member not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<MemberResponse> getMemberById(@PathVariable UUID id) {
        log.info("Received request to fetch member with id={}", id);

        MemberResponse response = memberService.getMemberById(id);

        log.info("Member fetched successfully with id={}", id);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get all members", description = "Returns a paginated list of all registered members")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Members fetched successfully",
                    content = @Content(schema = @Schema(implementation = MemberResponse.class)))
    })
    @GetMapping
    public ResponseEntity<Page<MemberResponse>> getAllMembers(
            @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {
        log.info("Received request to fetch all members: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());

        Page<MemberResponse> responses = memberService.getAllMembers(pageable);

        log.info("Fetched {} members (page {} of {})", responses.getNumberOfElements(),
                pageable.getPageNumber(), responses.getTotalPages());

        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Update an existing member", description = "Updates the details of an existing library member")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Member updated successfully",
                    content = @Content(schema = @Schema(implementation = MemberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Member not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already in use",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<MemberResponse> updateMember(
            @PathVariable UUID id,
            @Valid @RequestBody MemberRequest request) {
        log.info("Received request to update member with id={}", id);

        MemberResponse response = memberService.updateMember(id, request);

        log.info("Member updated successfully with id={}", id);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete a member", description = "Deletes a member by their unique identifier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Member deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Member not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMember(@PathVariable UUID id) {
        log.info("Received request to delete member with id={}", id);

        memberService.deleteMember(id);

        log.info("Member deleted successfully with id={}", id);

        return ResponseEntity.noContent().build();
    }
}