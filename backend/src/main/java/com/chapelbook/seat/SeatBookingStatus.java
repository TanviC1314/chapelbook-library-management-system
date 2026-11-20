package com.chapelbook.seat;

public enum SeatBookingStatus {
    /** Holds the slot. Only BOOKED rows take part in the no-overlap exclusion constraints. */
    BOOKED,
    /** Released: the slot can be booked again. */
    CANCELLED
}
