package com.dilnur.library_management.controller;

import com.dilnur.library_management.dto.request.BookRequest;
import com.dilnur.library_management.dto.response.BookResponse;
import com.dilnur.library_management.exception.ErrorResponse;
import com.dilnur.library_management.service.BookService;
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
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Books", description = "Endpoints for managing library books")
public class BookController {

    private final BookService bookService;

    @Operation(summary = "Create a new book", description = "Creates a new book and associates it with one or more existing authors")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Book created successfully",
                    content = @Content(schema = @Schema(implementation = BookResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "One or more authors not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "ISBN already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<BookResponse> createBook(@Valid @RequestBody BookRequest request) {
        log.info("Received request to create book: title={}, isbn={}", request.title(), request.isbn());

        BookResponse response = bookService.createBook(request);

        log.info("Book created successfully with id={}", response.id());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/books/" + response.id()))
                .body(response);
    }

    @Operation(summary = "Get book by id", description = "Returns a single book by its unique identifier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Book found",
                    content = @Content(schema = @Schema(implementation = BookResponse.class))),
            @ApiResponse(responseCode = "404", description = "Book not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<BookResponse> getBookById(@PathVariable UUID id) {
        log.info("Received request to fetch book with id={}", id);

        BookResponse response = bookService.getBookById(id);

        log.info("Book fetched successfully with id={}", id);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get all books", description = "Returns a paginated list of all books")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Books fetched successfully",
                    content = @Content(schema = @Schema(implementation = BookResponse.class)))
    })
    @GetMapping
    public ResponseEntity<Page<BookResponse>> getAllBooks(
            @PageableDefault(size = 20, sort = "title") Pageable pageable) {
        log.info("Received request to fetch all books: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());

        Page<BookResponse> responses = bookService.getAllBooks(pageable);

        log.info("Fetched {} books (page {} of {})", responses.getNumberOfElements(),
                pageable.getPageNumber(), responses.getTotalPages());

        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Update an existing book", description = "Updates the details of an existing book including its authors")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Book updated successfully",
                    content = @Content(schema = @Schema(implementation = BookResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Book or one or more authors not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "ISBN already in use by another book",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<BookResponse> updateBook(
            @PathVariable UUID id,
            @Valid @RequestBody BookRequest request) {
        log.info("Received request to update book with id={}", id);

        BookResponse response = bookService.updateBook(id, request);

        log.info("Book updated successfully with id={}", id);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete a book", description = "Deletes a book by its unique identifier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Book deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Book not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable UUID id) {
        log.info("Received request to delete book with id={}", id);

        bookService.deleteBook(id);

        log.info("Book deleted successfully with id={}", id);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Search books by title", description = "Returns a paginated list of books whose title contains the given keyword (case-insensitive)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search completed successfully",
                    content = @Content(schema = @Schema(implementation = BookResponse.class)))
    })
    @GetMapping("/search/title")
    public ResponseEntity<Page<BookResponse>> searchByTitle(
            @RequestParam String title,
            @PageableDefault(size = 20, sort = "title") Pageable pageable) {
        log.info("Received request to search books by title={}", title);

        Page<BookResponse> responses = bookService.searchByTitle(title, pageable);

        log.info("Search by title='{}' returned {} results", title, responses.getTotalElements());

        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Search books by author", description = "Returns a paginated list of books matching the given author name (case-insensitive)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search completed successfully",
                    content = @Content(schema = @Schema(implementation = BookResponse.class)))
    })
    @GetMapping("/search/author")
    public ResponseEntity<Page<BookResponse>> searchByAuthor(
            @RequestParam String authorName,
            @PageableDefault(size = 20, sort = "title") Pageable pageable) {
        log.info("Received request to search books by authorName={}", authorName);

        Page<BookResponse> responses = bookService.searchByAuthor(authorName, pageable);

        log.info("Search by authorName='{}' returned {} results", authorName, responses.getTotalElements());

        return ResponseEntity.ok(responses);
    }
}