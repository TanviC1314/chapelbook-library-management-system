package com.chapelbook.borrowing;

import com.chapelbook.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans")
public class LoanController {

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
}
