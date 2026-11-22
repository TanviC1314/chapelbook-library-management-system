package com.chapelbook.fine;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.chapelbook.common.PageResponse;
import com.chapelbook.security.AuthenticatedUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Fines", description = "Late fines: view, pay at the desk, waive.")
@RequestMapping("/api/v1/fines")
public class FineController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("assessedAt"), Sort.Order.desc("id"));

    private final FineService fines;

    public FineController(FineService fines) {
        this.fines = fines;
    }

    /** The student's own fines, newest first. */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('STUDENT')")
    public PageResponse<FineResponse> mine(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(required = false) FineStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return fines.mine(caller.id(), status, pageable(page, size));
    }

    /** How much the student owes, and whether that is enough to block borrowing. */
    @GetMapping("/mine/balance")
    @PreAuthorize("hasRole('STUDENT')")
    public FineBalanceResponse balance(@AuthenticationPrincipal AuthenticatedUser caller) {
        return fines.balance(caller.id());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public PageResponse<FineResponse> list(@RequestParam(required = false) Long userId,
            @RequestParam(required = false) FineStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return fines.list(userId, status, pageable(page, size));
    }

    /** A librarian records that the student paid this fine in full. */
    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public FineResponse pay(@PathVariable Long id) {
        return fines.pay(id);
    }

    /** Only an admin can cancel a fine. */
    @PostMapping("/{id}/waive")
    @PreAuthorize("hasRole('ADMIN')")
    public FineResponse waive(@PathVariable Long id) {
        return fines.waive(id);
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), NEWEST_FIRST);
    }
}
