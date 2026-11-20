package com.chapelbook.reservation;

public enum ReservationStatus {
    /** Waiting in the queue for a copy to be returned. */
    PENDING,
    /** A copy is held behind the desk and must be collected before {@code readyUntil}. */
    READY,
    /** The student collected the book. */
    FULFILLED,
    CANCELLED,
    /** The student did not collect in time. */
    EXPIRED;

    /** Active reservations still occupy the student's single slot for that title. */
    public boolean isActive() {
        return this == PENDING || this == READY;
    }
}
