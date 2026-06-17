package com.dilnur.library_management.service.impl;

import com.dilnur.library_management.config.ReservationProperties;
import com.dilnur.library_management.dto.request.BookRequest;
import com.dilnur.library_management.dto.response.BookResponse;
import com.dilnur.library_management.entity.Author;
import com.dilnur.library_management.entity.Book;
import com.dilnur.library_management.entity.Reservation;
import com.dilnur.library_management.entity.enums.ReservationStatus;
import com.dilnur.library_management.mapper.BookMapper;
import com.dilnur.library_management.repository.AuthorRepository;
import com.dilnur.library_management.repository.BookRepository;
import com.dilnur.library_management.repository.ReservationRepository;
import com.dilnur.library_management.service.BookService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final BookMapper bookMapper;
    private final ReservationRepository reservationRepository;
    private final ReservationProperties reservationProperties;

    @Override
    public BookResponse createBook(BookRequest bookRequest) {
        Book book = bookMapper.toEntity(bookRequest);

        List<Author> authors = authorRepository.findAllById(bookRequest.authorIds());
        if (authors.size() != bookRequest.authorIds().size()) {
            throw new EntityNotFoundException("One or more authors not found");
        }
        book.setAuthors(authors);

        Book saved = bookRepository.save(book);
        return bookMapper.toResponse(saved);
    }

    @Override
    public BookResponse getBookById(UUID id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found with id: " + id));
        return bookMapper.toResponse(book);
    }

    @Override
    public Page<BookResponse> getAllBooks(Pageable pageable) {
        return bookRepository.findAll(pageable).map(bookMapper::toResponse);
    }

    @Override
    public BookResponse updateBook(UUID id, BookRequest bookRequest) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found with id: " + id));

        book.setTitle(bookRequest.title());
        book.setIsbn(bookRequest.isbn());
        book.setGenre(bookRequest.genre());
        book.setPublicationYear(bookRequest.publicationYear());
        book.setTotalCopies(bookRequest.totalCopies());
        book.setAvailableCopies(bookRequest.availableCopies());

        List<Author> authors = authorRepository.findAllById(bookRequest.authorIds());
        if (authors.size() != bookRequest.authorIds().size()) {
            throw new EntityNotFoundException("One or more authors not found");
        }
        book.setAuthors(authors);

        Book updated = bookRepository.save(book);
        return bookMapper.toResponse(updated);
    }

    @Override
    public void deleteBook(UUID id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found with id: " + id));
        bookRepository.delete(book);
    }

    @Override
    public Page<BookResponse> searchByTitle(String title, Pageable pageable) {
        Page<Book> books = bookRepository.findByTitleContainingIgnoreCase(title, pageable);
        return bookMapper.toResponsePage(books);
    }

    @Override
    public Page<BookResponse> searchByAuthor(String authorName, Pageable pageable) {
        Page<Book> books = bookRepository.findByAuthorName(authorName, pageable);
        return bookMapper.toResponsePage(books);
    }

    @Override
    public void decreaseAvailableCopies(UUID id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found with id: " + id));

        if (book.getAvailableCopies() <= 0) {
            throw new IllegalStateException("No available copies for book: " + book.getTitle());
        }

        book.setAvailableCopies(book.getAvailableCopies() - 1);
        bookRepository.save(book);
    }

    @Override
    public void increaseAvailableCopies(UUID id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found with id: " + id));

        if (book.getAvailableCopies() >= book.getTotalCopies()) {
            throw new IllegalStateException("Available copies cannot exceed total copies for book: " + book.getTitle());
        }

        book.setAvailableCopies(book.getAvailableCopies() + 1);
        bookRepository.save(book);
    }

    @Override
    public Book getBookEntityById(UUID uuid) {
        return bookRepository.findById(uuid)
                .orElseThrow(() -> new EntityNotFoundException("Book not found with id: " + uuid));
    }

    @Override
    public void increaseAvailableCopiesOrFulfillReservation(UUID bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException("Book not found with id: " + bookId));

        List<Reservation> queue = reservationRepository
                .findByBookAndStatusOrderByReservedAtAsc(book, ReservationStatus.PENDING);

        if (queue.isEmpty()) {
            // no reservation queue — just increase available copies normally
            if (book.getAvailableCopies() >= book.getTotalCopies()) {
                throw new IllegalStateException("Available copies cannot exceed total copies for book: " + book.getTitle());
            }
            book.setAvailableCopies(book.getAvailableCopies() + 1);
        } else {
            // reservation queue exists — notify first member in queue
            Reservation nextReservation = queue.get(0);
            nextReservation.setStatus(ReservationStatus.NOTIFIED);
            nextReservation.setExpiresAt(LocalDate.now().plusDays(reservationProperties.getNotificationExpiryDays()));
            reservationRepository.save(nextReservation);

            // increase notifiedBooks instead of availableCopies
            book.setNotifiedBooks(book.getNotifiedBooks() + 1);
        }

        bookRepository.save(book);
    }

    @Override
    public Book saveBook(Book book) {
        return bookRepository.save(book);
    }
}