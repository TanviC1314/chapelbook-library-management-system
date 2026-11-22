package com.chapelbook.seat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create or edit a seat. {@code status} is optional: a new seat is ACTIVE and an edit keeps the current status. */
public record SeatRequest(
        @NotBlank @Size(max = 50) String label,
        @NotBlank @Size(max = 50) String zone,
        boolean hasPower,
        SeatStatus status) {
}
