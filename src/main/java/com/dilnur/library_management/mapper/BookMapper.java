package com.dilnur.library_management.mapper;

import com.dilnur.library_management.dto.request.BookRequest;
import com.dilnur.library_management.dto.response.BookResponse;
import com.dilnur.library_management.entity.Book;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring",uses = AuthorMapper.class)
public interface BookMapper {

    @Mapping(target = "authors", ignore = true)
    Book toEntity(BookRequest bookRequest);

    BookResponse toResponse(Book book);

    default Page<BookResponse> toResponsePage(Page<Book> books) {
        return books.map(this::toResponse);
    }}
