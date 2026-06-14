package com.dilnur.library_management.repository;

import com.dilnur.library_management.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookRepository extends JpaRepository<Book, UUID> {

    Optional<Book> findByIsbn(String isbn);

    Page<Book> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    Page<Book> findByAvailableCopiesGreaterThan(int availableCopies, Pageable pageable);

    @Query("SELECT b FROM Book b JOIN b.authors a WHERE LOWER(a.firstName) LIKE LOWER(CONCAT('%', :name, '%')) OR LOWER(a.lastName) LIKE LOWER(CONCAT('%', :name, '%'))")
    Page<Book> findByAuthorName(String name, Pageable pageable);

    @Query("SELECT b FROM Book b ORDER BY (b.totalCopies - b.availableCopies) DESC")
    Page<Book> findMostBorrowedBooks(Pageable pageable);
}
