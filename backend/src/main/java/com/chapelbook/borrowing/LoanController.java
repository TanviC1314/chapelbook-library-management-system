package com.chapelbook.borrowing;

import com.chapelbook.common.PageResponse;
import com.chapelbook.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans")
public class LoanController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("borrowedAt"), Sort.Order.desc("id"));

    private final BorrowingService borrowing;

    public LoanController(BorrowingService borrowing) {
        this.borrowing = borrowing;
    }

    /** A librarian lends a copy (by barcode) to a student. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public LoanResponse issue(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody IssueRequest request) {
        return borrowing.issue(caller, request.userId(), request.copyBarcode());
    }

    /** A librarian takes a copy back. The response carries the fine, if the book was late. */
    @PostMapping("/return")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public LoanResponse returnCopy(@Valid @RequestBody ReturnRequest request) {
        return borrowing.returnCopy(request.copyBarcode());
    }

    /** "My borrowings": the student's own loans, newest first. */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('STUDENT')")
    public PageResponse<LoanResponse> mine(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return borrowing.mine(caller.id(), activeOnly, pageable(page, size));
    }

    /** Staff: every loan, or only active or overdue ones, optionally for one student. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public PageResponse<LoanResponse> list(@RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "ALL") LoanScope scope,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return borrowing.list(userId, scope, pageable(page, size));
    }

    @GetMapping("/{id}")
    public LoanResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id) {
        return borrowing.get(caller, id);
    }

    /** Extends a loan. Students renew their own; staff can renew any. */
    @PostMapping("/{id}/renew")
    public LoanResponse renew(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id) {
        return borrowing.renew(caller, id);
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), NEWEST_FIRST);
    }
}
