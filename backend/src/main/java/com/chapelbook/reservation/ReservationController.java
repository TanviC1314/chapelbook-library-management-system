package com.chapelbook.reservation;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.chapelbook.common.PageResponse;
import com.chapelbook.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Reservations", description = "First-come first-served queue for a title, and held copies.")
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("reservedAt"), Sort.Order.desc("id"));

    private final ReservationService reservations;

    public ReservationController(ReservationService reservations) {
        this.reservations = reservations;
    }

    /** A student reserves a title: they are held a copy now, or join the queue. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STUDENT')")
    public ReservationResponse reserve(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody ReserveRequest request) {
        return reservations.reserve(caller.id(), request.bookId());
    }

    /** The student's own reservations, newest first. */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('STUDENT')")
    public PageResponse<ReservationResponse> mine(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return reservations.mine(caller.id(), pageable(page, size));
    }

    /** Staff see everyone's reservations, optionally filtered by status. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public PageResponse<ReservationResponse> list(@RequestParam(required = false) ReservationStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return reservations.list(status, pageable(page, size));
    }

    @GetMapping("/{id}")
    public ReservationResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id) {
        return reservations.get(id, caller);
    }

    /** Cancels a reservation. Owners cancel their own; staff can cancel any. */
    @DeleteMapping("/{id}")
    public ReservationResponse cancel(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id) {
        return reservations.cancel(id, caller);
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), NEWEST_FIRST);
    }
}
