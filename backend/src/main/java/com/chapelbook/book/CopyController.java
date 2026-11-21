package com.chapelbook.book;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Physical copies are staff business: students only ever see how many are available. */
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
public class CopyController {

    private final CopyService copies;

    public CopyController(CopyService copies) {
        this.copies = copies;
    }

    @GetMapping("/api/v1/books/{bookId}/copies")
    public List<CopyResponse> list(@PathVariable Long bookId) {
        return copies.list(bookId);
    }

    @PostMapping("/api/v1/books/{bookId}/copies")
    @ResponseStatus(HttpStatus.CREATED)
    public CopyResponse add(@PathVariable Long bookId, @Valid @RequestBody CopyRequest request) {
        return copies.add(bookId, request);
    }

    @PatchMapping("/api/v1/copies/{id}")
    public CopyResponse update(@PathVariable Long id, @Valid @RequestBody CopyUpdateRequest request) {
        return copies.update(id, request);
    }
}
