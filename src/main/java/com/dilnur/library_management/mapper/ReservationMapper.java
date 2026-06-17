package com.dilnur.library_management.mapper;

import com.dilnur.library_management.dto.response.ReservationResponse;
import com.dilnur.library_management.entity.Reservation;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReservationMapper {

    //to ReservationResponse
    ReservationResponse toResponse(Reservation reservation);


}
