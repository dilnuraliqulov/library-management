package com.dilnur.library_management.service.impl;

import com.dilnur.library_management.dto.request.AuthorRequest;
import com.dilnur.library_management.dto.response.AuthorResponse;
import com.dilnur.library_management.entity.Author;
import com.dilnur.library_management.mapper.AuthorMapper;
import com.dilnur.library_management.repository.AuthorRepository;
import com.dilnur.library_management.service.AuthorService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional

public class AuthorServiceImpl implements AuthorService {

    private final AuthorMapper authorMapper;
    private final AuthorRepository authorRepository;


    @Override
    public AuthorResponse createAuthor(AuthorRequest authorRequest) {
        Author author = authorMapper.toEntity(authorRequest);

        Author savedAuthor = authorRepository.save(author);

        return authorMapper.toResponse(savedAuthor);
    }

    @Override
    public AuthorResponse getAuthorById(UUID id) {
        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Author not found with id: " + id));

        return authorMapper.toResponse(author);
    }

    @Override
    public List<AuthorResponse> getAllAuthors() {

        return authorMapper.toResponseList(authorRepository.findAll());
    }

    @Override
    public AuthorResponse updateAuthor(UUID id, AuthorRequest authorRequest) {

        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Author not found with id: " + id));

        author.setFirstName(authorRequest.firstName());
        author.setLastName(authorRequest.lastName());
        author.setBio(authorRequest.bio());

        Author updatedAuthor = authorRepository.save(author);

        return authorMapper.toResponse(updatedAuthor);
    }

    @Override
    public void deleteAuthor(UUID id) {

        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Author not found with id: " + id));

        authorRepository.delete(author);

    }
}
