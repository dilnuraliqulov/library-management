package com.dilnur.library_management.mapper;

import com.dilnur.library_management.dto.request.AuthorRequest;
import com.dilnur.library_management.dto.response.AuthorResponse;
import com.dilnur.library_management.entity.Author;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AuthorMapper {

    Author toEntity(AuthorRequest authorRequest);

    AuthorRequest toRequest(Author author);

    List<AuthorResponse> toResponseList(List<Author> authors);

    AuthorResponse toResponse(Author author);

}
