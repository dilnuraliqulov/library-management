package com.dilnur.library_management.controller;

import com.dilnur.library_management.dto.request.AuthorRequest;
import com.dilnur.library_management.dto.response.AuthorResponse;
import com.dilnur.library_management.exception.ErrorResponse;
import com.dilnur.library_management.service.AuthorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/authors")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authors", description = "Endpoints for managing book authors")
public class AuthorController {

    private final AuthorService authorService;

    @Operation(summary = "Create a new author", description = "Creates a new author with the given first name, last name and bio")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Author created successfully",
                    content = @Content(schema = @Schema(implementation = AuthorResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<AuthorResponse> createAuthor(@Valid @RequestBody AuthorRequest request) {
        log.info("Received request to create author: firstName={}, lastName={}", request.firstName(), request.lastName());

        AuthorResponse response = authorService.createAuthor(request);

        log.info("Author created successfully with id={}", response.id());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/authors/" + response.id()))
                .body(response);
    }

    @Operation(summary = "Get author by id", description = "Returns a single author by their unique identifier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Author found",
                    content = @Content(schema = @Schema(implementation = AuthorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Author not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<AuthorResponse> getAuthorById(@PathVariable UUID id) {
        log.info("Received request to fetch author with id={}", id);

        AuthorResponse response = authorService.getAuthorById(id);

        log.info("Author fetched successfully with id={}", id);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get all authors", description = "Returns the list of all registered authors")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authors fetched successfully",
                    content = @Content(schema = @Schema(implementation = AuthorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<AuthorResponse>> getAllAuthors() {
        log.info("Received request to fetch all authors");

        List<AuthorResponse> responses = authorService.getAllAuthors();

        log.info("Fetched {} authors", responses.size());

        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Update an existing author", description = "Updates the first name, last name and bio of an existing author")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Author updated successfully",
                    content = @Content(schema = @Schema(implementation = AuthorResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Author not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<AuthorResponse> updateAuthor(
            @PathVariable UUID id,
            @Valid @RequestBody AuthorRequest request) {
        log.info("Received request to update author with id={}", id);

        AuthorResponse response = authorService.updateAuthor(id, request);

        log.info("Author updated successfully with id={}", id);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete an author", description = "Deletes an author by their unique identifier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Author deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Author not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAuthor(@PathVariable UUID id) {
        log.info("Received request to delete author with id={}", id);

        authorService.deleteAuthor(id);

        log.info("Author deleted successfully with id={}", id);

        return ResponseEntity.noContent().build();
    }
}