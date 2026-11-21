package com.chapelbook.reservation;

import java.time.Instant;

/**
 * A reservation as shown to its owner or to staff. {@code queuePosition} is 1 for the next student in
 * line and only present while the reservation is PENDING; {@code copyBarcode} and {@code readyUntil}
 * are present while a copy is being held (READY).
 */
public record ReservationResponse(
        Long id,
        Long bookId,
        String bookTitle,
        Long userId,
        String userName,
        ReservationStatus status,
        Instant reservedAt,
        Instant readyUntil,
        String copyBarcode,
        Long queuePosition) {

    public static ReservationResponse from(Reservation r, Long queuePosition) {
        boolean holding = r.getStatus() == ReservationStatus.READY && r.getCopy() != null;
        return new ReservationResponse(r.getId(), r.getBook().getId(), r.getBook().getTitle(), r.getUser().getId(),
                r.getUser().getFullName(), r.getStatus(), r.getReservedAt(), r.getReadyUntil(),
                holding ? r.getCopy().getBarcode() : null, queuePosition);
    }
}
