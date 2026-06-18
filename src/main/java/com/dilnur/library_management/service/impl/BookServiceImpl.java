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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final BookMapper bookMapper;
    private final ReservationRepository reservationRepository;
    private final ReservationProperties reservationProperties;

    @Override
    public BookResponse createBook(BookRequest bookRequest) {
        log.info("Creating book: title={}, isbn={}", bookRequest.title(), bookRequest.isbn());

        Book book = bookMapper.toEntity(bookRequest);

        List<Author> authors = authorRepository.findAllById(bookRequest.authorIds());
        if (authors.size() != bookRequest.authorIds().size()) {
            log.warn("Author id mismatch — requested={}, found={}", bookRequest.authorIds().size(), authors.size());

            throw new EntityNotFoundException("One or more authors not found");
        }
        book.setAuthors(authors);

        Book saved = bookRepository.save(book);
        log.info("Book created successfully with id={}", saved.getId());

        return bookMapper.toResponse(saved);
    }

    @Override
    public BookResponse getBookById(UUID id) {
        log.debug("Fetching book with id={}", id);

        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found with id: " + id));

        log.debug("Book fetched successfully with id={}", id);

        return bookMapper.toResponse(book);
    }

    @Override
    public Page<BookResponse> getAllBooks(Pageable pageable) {
        log.debug("Fetching all books: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());

        return bookRepository.findAll(pageable).map(bookMapper::toResponse);
    }

    @Override
    public BookResponse updateBook(UUID id, BookRequest bookRequest) {
        log.info("Updating book with id={}", id);

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
        log.info("Book updated successfully with id={}", id);

        return bookMapper.toResponse(updated);
    }

    @Override
    public void deleteBook(UUID id) {
        log.info("Deleting book with id={}", id);

        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found with id: " + id));
        bookRepository.delete(book);
        log.info("Book deleted successfully with id={}", id);

    }

    @Override
    public Page<BookResponse> searchByTitle(String title, Pageable pageable) {
        log.debug("Searching books by title='{}': page={}, size={}", title, pageable.getPageNumber(), pageable.getPageSize());

        Page<Book> books = bookRepository.findByTitleContainingIgnoreCase(title, pageable);

        return bookMapper.toResponsePage(books);
    }

    @Override
    public Page<BookResponse> searchByAuthor(String authorName, Pageable pageable) {
        log.debug("Searching books by authorName='{}': page={}, size={}", authorName, pageable.getPageNumber(), pageable.getPageSize());

        Page<Book> books = bookRepository.findByAuthorName(authorName, pageable);

        return bookMapper.toResponsePage(books);
    }

    @Override
    public void decreaseAvailableCopies(UUID id) {
        log.debug("Decreasing available copies for bookId={}", id);

        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found with id: " + id));

        if (book.getAvailableCopies() <= 0) {
            throw new IllegalStateException("No available copies for book: " + book.getTitle());
        }

        book.setAvailableCopies(book.getAvailableCopies() - 1);
        log.debug("Available copies decreased for bookId={}, remaining={}", id, book.getAvailableCopies());

        bookRepository.save(book);
    }

    @Override
    public void increaseAvailableCopies(UUID id) {
        log.debug("Increasing available copies for bookId={}", id);

        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found with id: " + id));

        if (book.getAvailableCopies() >= book.getTotalCopies()) {
            throw new IllegalStateException("Available copies cannot exceed total copies for book: " + book.getTitle());
        }

        book.setAvailableCopies(book.getAvailableCopies() + 1);
        log.debug("Available copies increased for bookId={}, now={}", id, book.getAvailableCopies());

        bookRepository.save(book);
    }

    @Override
    public Book getBookEntityById(UUID uuid) {
        log.debug("Fetching book entity with id={}", uuid);

        return bookRepository.findById(uuid)
                .orElseThrow(() -> new EntityNotFoundException("Book not found with id: " + uuid));
    }

    @Override
    public void increaseAvailableCopiesOrFulfillReservation(UUID bookId) {
        log.debug("Processing copy return for bookId={}", bookId);

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
        log.debug("Saving book with id={}", book.getId());

        return bookRepository.save(book);
    }
}