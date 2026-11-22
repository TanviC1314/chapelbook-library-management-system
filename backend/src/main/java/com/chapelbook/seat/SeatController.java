package com.chapelbook.seat;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.chapelbook.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@Tag(name = "Seats", description = "Study seats and their availability.")
@RequestMapping("/api/v1/seats")
public class SeatController {

    private final SeatService seats;

    public SeatController(SeatService seats) {
        this.seats = seats;
    }

    /**
     * The seats. Pass {@code from} and {@code to} (ISO-8601 instants) to see which are free for that whole window.
     * Staff also see seats that are out of service.
     */
    @GetMapping
    public List<SeatResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        boolean staff = caller.role() != com.chapelbook.user.Role.STUDENT;
        return seats.list(staff, zone, from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public SeatResponse create(@Valid @RequestBody SeatRequest request) {
        return seats.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public SeatResponse update(@PathVariable Long id, @Valid @RequestBody SeatRequest request) {
        return seats.update(id, request);
    }
}
