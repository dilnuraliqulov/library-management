package com.dilnur.library_management.service.impl;

import com.dilnur.library_management.dto.request.AuthorRequest;
import com.dilnur.library_management.dto.response.AuthorResponse;
import com.dilnur.library_management.entity.Author;
import com.dilnur.library_management.exception.BusinessRuleException;
import com.dilnur.library_management.mapper.AuthorMapper;
import com.dilnur.library_management.repository.AuthorRepository;
import com.dilnur.library_management.repository.BookRepository;
import com.dilnur.library_management.service.AuthorService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AuthorServiceImpl implements AuthorService {

    private final AuthorMapper authorMapper;
    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;


    @Override
    public AuthorResponse createAuthor(AuthorRequest authorRequest) {
        log.info("Creating author: firstName={}, lastName={}", authorRequest.firstName(), authorRequest.lastName());

        Author author = authorMapper.toEntity(authorRequest);

        Author savedAuthor = authorRepository.save(author);

        log.info("Author created successfully with id={}", savedAuthor.getId());

        return authorMapper.toResponse(savedAuthor);
    }

    @Override
    public AuthorResponse getAuthorById(UUID id) {
        log.debug("Fetching author with id={}", id);

        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Author not found with id: " + id));

        log.debug("Author fetched successfully with id={}", id);

        return authorMapper.toResponse(author);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuthorResponse> getAllAuthors(Pageable pageable) {
        log.debug("Fetching authors page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        return authorMapper.toResponsePage(authorRepository.findAll(pageable));
    }

    @Override
    public AuthorResponse updateAuthor(UUID id, AuthorRequest authorRequest) {
        log.info("Updating author with id={}", id);

        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Author not found with id: " + id));

        author.setFirstName(authorRequest.firstName());
        author.setLastName(authorRequest.lastName());
        author.setBio(authorRequest.bio());

        Author updatedAuthor = authorRepository.save(author);
        log.info("Author updated successfully with id={}", id);

        return authorMapper.toResponse(updatedAuthor);
    }

    @Override
    public void deleteAuthor(UUID id) {
        log.info("Deleting author with id={}", id);

        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Author not found with id: " + id));

        boolean hasBooks = bookRepository.existsByAuthorsContaining(author);
        if (hasBooks) {
            throw new BusinessRuleException(
                    "Cannot delete author with id=" + id +
                            ": author is linked to one or more books. " +
                            "Remove the author from all books first."
            );
        }

        authorRepository.delete(author);
        log.info("Author deleted successfully with id={}", id);
    }
}
