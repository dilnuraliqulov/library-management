package com.dilnur.library_management.service;

import com.dilnur.library_management.dto.request.BookRequest;
import com.dilnur.library_management.dto.response.BookResponse;
import com.dilnur.library_management.entity.Book;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface BookService {

    BookResponse createBook(BookRequest bookRequest);

    BookResponse getBookById(UUID id);

    Page<BookResponse> getAllBooks( Pageable pageable);

    BookResponse updateBook(UUID id,BookRequest bookRequest);

    void deleteBook(UUID id);

    Page<BookResponse> searchByTitle(String title, Pageable pageable);

    Page<BookResponse> searchByAuthor(String authorName, Pageable pageable);


    void decreaseAvailableCopies(UUID id);
    void increaseAvailableCopies(UUID id);

    Book getBookEntityById(@NotNull(message = "Book id must not be null") UUID uuid);
}
