package com.dilnur.library_management.service;

import com.dilnur.library_management.dto.request.ReservationRequest;
import com.dilnur.library_management.dto.response.ReservationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReservationService {

    // Task 5 — reserve a book
    ReservationResponse reserveBook(ReservationRequest request);

    // Task 5 — cancel reservation
    ReservationResponse cancelReservation(UUID reservationId);

    // general read
    ReservationResponse getReservationById(UUID id);
    Page<ReservationResponse> getAllReservations(Pageable pageable);
    Page<ReservationResponse> getReservationsByMember(UUID memberId, Pageable pageable);
}