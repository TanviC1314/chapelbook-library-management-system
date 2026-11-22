package com.chapelbook.book;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.chapelbook.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Books", description = "The catalogue: titles, search and availability.")
@RequestMapping("/api/v1/books")
public class BookController {

    private final BookService books;

    public BookController(BookService books) {
        this.books = books;
    }

    /**
     * The catalogue. {@code q} matches title, author or ISBN (every word must match);
     * {@code availableOnly=true} hides titles with no copy on the shelf.
     */
    @GetMapping
    public PageResponse<BookResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "false") boolean availableOnly,
            @RequestParam(defaultValue = "TITLE") BookSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return books.search(q, categoryId, availableOnly, sort, page, size);
    }

    @GetMapping("/{id}")
    public BookResponse get(@PathVariable Long id) {
        return books.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public BookResponse create(@Valid @RequestBody BookRequest request) {
        return books.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public BookResponse update(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        return books.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public void delete(@PathVariable Long id) {
        books.delete(id);
    }
}
