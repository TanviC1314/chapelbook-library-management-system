package com.chapelbook.seat;

public enum SeatStatus {
    ACTIVE,
    /** Temporarily unavailable (broken, closed zone). Cannot be booked. */
    OUT_OF_SERVICE
}
