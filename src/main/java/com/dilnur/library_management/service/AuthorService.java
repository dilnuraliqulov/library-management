package com.dilnur.library_management.service;

import com.dilnur.library_management.dto.request.AuthorRequest;
import com.dilnur.library_management.dto.response.AuthorResponse;

import java.util.List;
import java.util.UUID;

public interface AuthorService {

    AuthorResponse createAuthor(AuthorRequest authorRequest);

    AuthorResponse getAuthorById(UUID id);

    List<AuthorResponse> getAllAuthors();

    AuthorResponse updateAuthor(UUID id,AuthorRequest authorRequest);

    void deleteAuthor(UUID id);
}
