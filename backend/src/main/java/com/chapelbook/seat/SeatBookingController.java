package com.chapelbook.seat;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.chapelbook.common.PageResponse;
import com.chapelbook.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
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
@Tag(name = "Seat bookings", description = "Booking a study seat for a time window.")
@RequestMapping("/api/v1/seat-bookings")
public class SeatBookingController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("startsAt"), Sort.Order.desc("id"));

    private final SeatService seats;

    public SeatBookingController(SeatService seats) {
        this.seats = seats;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STUDENT')")
    public SeatBookingResponse book(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody BookSeatRequest request) {
        return seats.book(caller.id(), request.seatId(), request.startsAt(), request.endsAt());
    }

    /** The student's own bookings, latest first. */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('STUDENT')")
    public PageResponse<SeatBookingResponse> mine(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(defaultValue = "false") boolean upcomingOnly,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return seats.mine(caller.id(), upcomingOnly, pageable(page, size));
    }

    /** Staff: all bookings, or the active ones overlapping a time window. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public PageResponse<SeatBookingResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return seats.list(from, to, pageable(page, size));
    }

    /** Cancels a booking and frees the seat. Owners cancel their own; staff can cancel any. */
    @DeleteMapping("/{id}")
    public SeatBookingResponse cancel(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id) {
        return seats.cancel(caller, id);
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), LATEST_FIRST);
    }
}
