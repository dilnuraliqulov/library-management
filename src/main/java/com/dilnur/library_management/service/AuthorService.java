package com.dilnur.library_management.service;

import com.dilnur.library_management.dto.request.AuthorRequest;
import com.dilnur.library_management.dto.response.AuthorResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AuthorService {

    AuthorResponse createAuthor(AuthorRequest authorRequest);

    AuthorResponse getAuthorById(UUID id);

    Page<AuthorResponse> getAllAuthors(Pageable pageable);

    AuthorResponse updateAuthor(UUID id,AuthorRequest authorRequest);

    void deleteAuthor(UUID id);
}
