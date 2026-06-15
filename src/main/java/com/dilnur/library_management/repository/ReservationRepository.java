package com.dilnur.library_management.repository;

import com.dilnur.library_management.entity.Book;
import com.dilnur.library_management.entity.enums.ReservationStatus;
import com.dilnur.library_management.entity.Member;
import com.dilnur.library_management.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByBookAndStatusOrderByReservedAtAsc(Book book, ReservationStatus status);

    Optional<Reservation> findByMemberAndBookAndStatus(Member member, Book book, ReservationStatus status);
}