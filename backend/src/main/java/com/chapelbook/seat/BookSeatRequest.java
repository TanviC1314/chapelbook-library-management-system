package com.chapelbook.seat;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Book this seat from {@code startsAt} (inclusive) to {@code endsAt} (exclusive). */
public record BookSeatRequest(
        @NotNull Long seatId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt) {
}
